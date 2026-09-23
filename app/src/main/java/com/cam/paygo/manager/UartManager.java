package com.cam.paygo.manager;

import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cam.paygo.bank.ReceiptData;
import com.cam.paygo.constants.BankConstants;
import com.cam.paygo.constants.JsonKeys;
import com.cam.paygo.constants.StatusConstants;
import com.cam.paygo.constants.TvmErrorCodes;
import com.cam.paygo.constants.TxnConstants;
import com.cam.paygo.constants.UartConstants;
import com.cam.paygo.utils.AppLogger;
import com.cam.paygo.utils.PaxDeviceHelper;
import com.pax.dal.IDAL;
import com.pax.dal.IComm;
import com.pax.dal.entity.EUartPort;
import com.pax.dal.entity.UartParam;
import com.pax.dal.exceptions.CommException;
import com.pax.neptunelite.api.NeptuneLiteUser;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class UartManager {
    private static final String TAG = "UartManager";
    private static UartManager instance;
    private final Context context;
    public IComm uartComm;

    private UartCallback callback;

    private volatile boolean listening = false;
    private String deviceSerialNumber, errorCodeTvm;
    private final Object uartLock = new Object();
    private boolean reinitializing = false;

    public interface UartCallback {
        void onDataReceived(String data, String dbTxnId);
    }

    public UartManager(Context ctx) {
        this.context = ctx.getApplicationContext();
        initUart();
    }

    public static synchronized UartManager getInstance(Context ctx) {
        if (instance == null) {
            instance = new UartManager(ctx);
        }
        return instance;
    }

    private void initUart() {
        if (!PaxDeviceHelper.isPaxDevice()) {
            Log.w(TAG, "skip UART init — not a PAX device (no libpaxapijni.so)");
            uartComm = null;
            return;
        }
        try {
            String model = android.os.Build.MODEL;
            Log.d(TAG, "Device model: " + model);

            UartParam uartParam = new UartParam();
            uartParam.setPort(model.equals(BankConstants.DEVICE_MODEL_IM30) ? EUartPort.USBDEV : EUartPort.COM1);
            uartParam.setAttr(UartConstants.UART_ATTR);

            IDAL dal = NeptuneLiteUser.getInstance().getDal(context);
            uartComm = dal.getCommManager().getUartComm(uartParam);

            Log.d(TAG, "UART initialized on port: " + uartParam.getPort().toString());

        } catch (Exception e) {
            Log.e(TAG, "UART init failed: " + e.getMessage(), e);
            uartComm = null;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            Log.e(TAG, "UART init failed — PAX native lib missing: " + e.getMessage(), e);
            uartComm = null;
        }
    }

    public void setCallback(UartCallback callback) {
        this.callback = callback;
    }

    public boolean connect() {
        if (!IntegrationModeStore.isUsb(context)) {
            Log.i(TAG, "skip UART connect — mode=" + IntegrationModeStore.get(context));
            return false;
        }
        if (!PaxDeviceHelper.isPaxDevice()) {
            Log.w(TAG, "skip UART connect — not a PAX device");
            return false;
        }
        try {
            if (uartComm != null) {
                uartComm.connect();
                Log.d(TAG, "UART connected");
                return true;
            }
        } catch (CommException e) {
            Log.e(TAG, "UART connect failed: " + e.getMessage());
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            Log.e(TAG, "UART connect failed — PAX native lib missing: " + e.getMessage(), e);
            uartComm = null;
        }
        return false;
    }

    public void disconnect() {
        try {
            listening = false;
            if (uartComm != null) {
                uartComm.disconnect();
                Log.d(TAG, "UART disconnected");
            }
        } catch (CommException e) {
            Log.e(TAG, "UART disconnect failed: " + e.getMessage());
        }
    }

    public void handleUsbDetached() {
        listening = false;
        disconnect();
    }

    public void handleUsbAttached() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            reinitialize();   // create fresh object

            if (connect()) {
                startListening();
                Log.d(TAG, "Reconnected successfully");
            } else {
                Log.e(TAG, "Reconnect failed");
            }

        }, UartConstants.USB_RECONNECT_DELAY_MS);
    }

    public void reinitialize() {
        disconnect();
        initUart();
    }

    public void startListening() {
        if (!IntegrationModeStore.isUsb(context)) {
            Log.i(TAG, "skip UART listen — mode=" + IntegrationModeStore.get(context));
            return;
        }
        if (listening) return;
        listening = true;
        Log.d(TAG, "Inside Start Listening : " + timeStamp());

        new Thread(() -> {
            StringBuilder buffer = new StringBuilder();

            while (listening) {
                try {
                    IComm comm;
                    synchronized (uartLock) {
                        comm = uartComm;
                    }

                    // Port missing (e.g. after failed recovery) — recreate safely
                    if (comm == null) {
                        Log.e(TAG, "UART Comm is null. Reinitializing...");
                        if (!tryReinitialize()) {
                            Thread.sleep(UartConstants.RECOVERY_DELAY_MS);
                        }
                        continue;
                    }

                    // Re-apply on every loop so a new uartComm after recovery is covered
                    try {
                        comm.setRecvTimeout(UartConstants.RECV_TIMEOUT_MS);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to set UART receive timeout", e);
                    }

                    byte[] result;
                    try {
                        result = comm.recv(UartConstants.RECV_BUFFER_BYTES);
                    } catch (CommException e) {
                        // COMM#3 / PortException -3 is usually recv timeout (no data).
                        // Do NOT call reinitialize() — that was killing the listener.
                        if (isRecvTimeout(e)) {
                            continue;
                        }

                        Log.e(TAG, "UART COMM ERROR", e);
                        if (!tryReinitialize()) {
                            Thread.sleep(UartConstants.RECOVERY_DELAY_MS);
                        } else {
                            // Wait for port to settle before next recv()
                            Thread.sleep(UartConstants.RECOVERY_DELAY_MS);
                        }
                        continue;
                    }

                    if (result != null && result.length > 0) {
                        String chunk = new String(result, StandardCharsets.UTF_8);
                        buffer.append(chunk);
                        Log.d(TAG, "UART Chunk: " + chunk);

                        // Check for full JSON (}} or matched braces)
//                        int open = buffer.length() - buffer.toString().replace("{", "").length();
//                        int close = buffer.length() - buffer.toString().replace("}", "").length();
                        long open = buffer.chars().filter(ch -> ch == '{').count();
                        long close = buffer.chars().filter(ch -> ch == '}').count();

                        if (open > 0 && open == close) {
                            String fullMessage = buffer.toString().trim();
                            buffer.setLength(0);

                            Log.d(TAG, "UART Full Message: " + fullMessage);

                            try {
                                JSONObject root = new JSONObject(fullMessage);
                                JSONObject data = root.getJSONObject(JsonKeys.DATA);

                                String requestType = root.optString(JsonKeys.REQUEST_TYPE);
                                String tranType = data.optString(JsonKeys.TRAN_TYPE);
                                String operatorOrderId = data.optString(JsonKeys.OPERATOR_ORDER_ID);
                                if (Objects.equals(operatorOrderId, "")) {
                                    Log.d(TAG, "Generating new operatorOrderId");
                                    operatorOrderId = generateOperatorOrderId();
                                }
                                String stationId = data.optString(JsonKeys.STATION_ID);
                                String gateNo = data.optString(JsonKeys.GATENO);
                                String stationName = data.optString(JsonKeys.STATION_NAME);
                                String shiftNo = data.optString(JsonKeys.SHIFT_NO);

                                AppLogger.trxn_log(context, BankConstants.KIOSK_INBOUND, fullMessage);

                                if (callback != null) {
                                    callback.onDataReceived(fullMessage, "");
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Failed to parse or log kiosk request", e);
                            }
                        }
                    } else {
                        Log.d(TAG, "Nothing to receive");
                    }

                    Thread.sleep(UartConstants.LISTENER_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.d(TAG, "UART listener interrupted");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "UART read error: " + e.getMessage(), e);
                    listening = false;
                }
            }
            Log.d(TAG, "UART listener stopped");
        }, "UART-Listener").start();
    }

    /** PAX often throws COMM#3 when recv() times out with no bytes. */
    private boolean isRecvTimeout(CommException e) {
        if (e == null) return false;

        try {
            java.lang.reflect.Method getErrCode = e.getClass().getMethod("getErrCode");
            Object code = getErrCode.invoke(e);
            if (code instanceof Integer && ((Integer) code) == 3) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }

        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase(Locale.US);
        return lower.contains("comm#3")
                || lower.contains("recv error")
                || lower.contains("-3");
    }

    /**
     * Thread-safe recovery for real port failures.
     * Does not clear {@code listening} (unlike {@link #reinitialize()}).
     */
    private boolean tryReinitialize() {
        synchronized (uartLock) {
            if (reinitializing) {
                Log.d(TAG, "UART reinitialization already in progress");
                return false;
            }
            reinitializing = true;
            try {
                Log.d(TAG, "Starting UART reinitialization...");

                if (uartComm != null) {
                    try {
                        uartComm.disconnect();
                    } catch (Exception e) {
                        Log.e(TAG, "Error disconnecting old UART", e);
                    }
                    uartComm = null;
                }

                Thread.sleep(UartConstants.REINIT_SETTLE_MS);
                initUart();

                if (uartComm == null) {
                    Log.e(TAG, "UART initialization returned null");
                    return false;
                }

                uartComm.connect();
                uartComm.setRecvTimeout(UartConstants.RECV_TIMEOUT_MS);
                Log.d(TAG, "UART reinitialized successfully");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "UART reinitialization failed", e);
                uartComm = null;
                return false;
            } finally {
                reinitializing = false;
            }
        }
    }

    private String generateOperatorOrderId() {
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmssSSS",
                java.util.Locale.getDefault()).format(new java.util.Date());
        int randomDigits = new java.util.Random().nextInt(9000) + 1000; // 1000–9999
        return timestamp + randomDigits;

    }

    private String getErrorCodeTVM(String statusCode, String data) {

        if (statusCode == null || data == null) {
            return "";
        }

        String errorCodeTvm = "";

        switch (statusCode) {

            case TvmErrorCodes.BANK_STATUS_01:
                if (TvmErrorCodes.MSG_ICC_CMD_ERROR.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "01";
                } else if (TvmErrorCodes.MSG_SOCKET_CONNECTION_FAILED.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "02";
                } else if (TvmErrorCodes.MSG_UNABLE_TO_REACH_SERVER.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "03";
                } else if (TvmErrorCodes.MSG_HSM_CONNECTION_FAILED.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "04";
                } else if (TvmErrorCodes.MSG_DATA_ERROR.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "05";
                } else if (TvmErrorCodes.MSG_DATABASE_FAILURE.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "06";
                } else if (TvmErrorCodes.MSG_DECLINE.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "07";
                } else if (TvmErrorCodes.MSG_DECLINE_DUE_TO_NO_SERVICE_FOUND.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "08";
                } else if (TvmErrorCodes.MSG_DUPLICATE_TRANSACTION.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "09";
                } else if (TvmErrorCodes.MSG_TERMINATE.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "10";
                }  else if (TvmErrorCodes.MSG_PPSE_ERROR.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "11";
                } else if (TvmErrorCodes.MSG_DUPLICATE_REQUEST.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "12";
                } else {
                    errorCodeTvm = "F8" + statusCode;
                }
                break;

            case TvmErrorCodes.BANK_STATUS_1102:
                if (TvmErrorCodes.MSG_TRANSACTION_CANCELLED.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "01";
                } else if (TvmErrorCodes.MSG_TIMEOUT.equalsIgnoreCase(data.trim())) {
                    errorCodeTvm = "F8" + statusCode + "02";
                } else {
                    errorCodeTvm = "F8" + statusCode;
                }
                break;

            case StatusConstants.APB_TIMEOUT:
                errorCodeTvm = TvmErrorCodes.TVM_APB_TIMEOUT;

                break;

            default:
                errorCodeTvm = "F8" + statusCode;
                break;
        }

        return errorCodeTvm;
    }

    public void sendErrorResponse(String tranType, String statusCode, String data, String ErpTranId, String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();
        errorCodeTvm = getErrorCodeTVM(statusCode, data);
        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();
                json.put(JsonKeys.STATUS, Objects.equals(statusCode, StatusConstants.APB_TIMEOUT) ? StatusConstants.APB_TIMEOUT : StatusConstants.FAILED);
                json.put(JsonKeys.STATUS_CODE, statusCode);
                json.put(JsonKeys.TRAN_TYPE, tranType);
                json.put(JsonKeys.ERP_TRAN_ID, ErpTranId);
                json.put(JsonKeys.DEVICE_SERIAL_NUMBER, deviceSerialNumber);
                json.put(JsonKeys.ERROR_CODE_TVM, errorCodeTvm);
                json.put(JsonKeys.MESSAGE, data);

                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put(JsonKeys.ABP_RAW_JSON, abpJson);
                } catch (Exception ex) {
                    // fallback if invalid JSON
                    json.put(JsonKeys.ABP_RAW_JSON, ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Error Response: " + finalJson);

                AppLogger.trxn_log(context, BankConstants.KIOSK_OUTBOUND, finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendAnyReceiptResponse(String statusCode,
                                       String responseType,
                                       String message,
                                       String TranType,
                                       String TxnID,
                                       String CrdType,
                                       String BaseAmount,
                                       String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("RESPONSE_TYPE", responseType);
                json.put("STATUS_CODE", statusCode);
                json.put("MESSAGE", message);
                json.put("TxnID", TxnID);
                json.put("CARD_TYPE", CrdType);
                json.put("TRAN_TYPE", TranType);
                json.put("BASE_AMOUNT", BaseAmount);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper handling of nested JSON
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    // fallback if invalid JSON
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Any Receipt Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendAnyRefundResponse(
            String operatorOrderId,
            String operatorRefundId,
            String TERMINAL_ID,
            String TRANSACTION,
            String AMOUNT,
            String MERCHANT_ID,
            String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("OPERATOR_REFUND_ID", operatorRefundId);
                json.put("TERMINAL_ID", TERMINAL_ID);
                json.put("TRANSACTION", TRANSACTION);
                json.put("AMOUNT", AMOUNT);
                json.put("MERCHANT_ID", MERCHANT_ID);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper nested JSON handling
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    // fallback if invalid JSON
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Refund Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendTransactionEnquiryResponse(
            String statusCode,
            String responseType,
            String message,
            String tranType,
            String operatorOrderId,
            String TipAmount,
            String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("TRAN_TYPE", tranType);
                json.put("TIP_AMOUNT", TipAmount);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    // fallback if not valid JSON
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Transaction Enquiry Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendVoidResponse(
            String statusCode,
            String responseType,
            String message,
            String tranType,
            String operatorOrderId,
            String SaleAmt,
            String RRN,
            String MID,
            String InvoiceNr,
            String TxnID,
            String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("TRANSACTION_TYPE", tranType);
                json.put("SALE_AMOUNT", SaleAmt);
                json.put("RRN", RRN);
                json.put("MID", MID);
                json.put("INVOICE_NR", InvoiceNr);
                json.put("TxnID", TxnID);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper nested JSON handling
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON); // fallback
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Void Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendBalanceEnquiryResponse(String responseType,
                                           String statusCode,
                                           String message,
                                           String AVALBALANCE,
                                           String TOP_UP_AMOUNT,
                                           String cardType,
                                           String last4Digit,
                                           String dateTime,
                                           String RRN,
                                           String TID,
                                           String MID,
                                           String tranType,
                                           String operatorOrderId,
                                           String AID,
                                           String TipAmount,
                                           String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();
                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("TRAN_TYPE", tranType);
                json.put("CARD_TYPE", cardType);
                json.put("LAST_4_DIGIT", last4Digit);
                json.put("DATE_TIME", dateTime);
                json.put("RRN", RRN);
                json.put("TID", TID);
                json.put("MID", MID);
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("AID", AID);
                json.put("AVALBALANCE", AVALBALANCE);
                json.put("TOP_UP_AMOUNT", TOP_UP_AMOUNT);
                json.put("TIP_AMOUNT", TipAmount);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // KEY FIX: Add raw JSON properly
                JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                json.put("ABP_RAW_JSON", abpJson);

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Balance Enquiry Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }
        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendBalanceUpdateResponse(String statusCode,
                                          String responseType,
                                          String message,
                                          String AVALBALANCE,
                                          String TOP_UP_AMOUNT,
                                          String TranType,
                                          String TxnID,
                                          String OperatorOrderId,
                                          String CrdType,
                                          String DateTime,
                                          String InvoiceNr,
                                          String Last4Digit,
                                          String TID,
                                          String MID,
                                          String AID,
                                          String RRN,
                                          String TipAmount,
                                          String TSI,
                                          String TVR,
                                          String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("TRAN_TYPE", TranType);
                json.put("TxnID", TxnID);
                json.put("OPERATOR_ORDER_ID", OperatorOrderId);
                json.put("CARD_TYPE", CrdType);
                json.put("DATE_TIME", DateTime);
                json.put("INVOICE_NR", InvoiceNr);
                json.put("LAST_4_DIGIT", Last4Digit);
                json.put("TID", TID);
                json.put("MID", MID);
                json.put("AID", AID);
                json.put("RRN", RRN);
                json.put("AVALBALANCE", AVALBALANCE);
                json.put("TOP_UP_AMOUNT", TOP_UP_AMOUNT);
                json.put("TIP_AMOUNT", TipAmount);
                json.put("TSI", TSI);
                json.put("TVR", TVR);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // KEY FIX (same as before)
                JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                json.put("ABP_RAW_JSON", abpJson);

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Balance Update Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void serviceCreationSuccessResponse(
            String statusCode,
            String responseType,
            String message,
            String TranType,
            String operatorOrderId,
            String MID,
            String TID,
            String BaseAmount,
            String RRN,
            String TxnID,
            String ABP_RAW_JSON
    ) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("TRAN_TYPE", TranType);
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("MID", MID);
                json.put("TID", TID);
                json.put("RRN", RRN);
                json.put("BASE_AMOUNT", BaseAmount);
                json.put("TxnID", TxnID);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper nested JSON handling
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON); // fallback
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Service Creation Balance : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendCreditDebitResponse(String uid, String balance, String topupAmount) {
        deviceSerialNumber = getDeviceSerialNumber();
        try {
            if (uartComm != null) {
                // Build JSON string
                String jsonResponse = "{"
                        + "\"TRAN_TYPE\": \"CREDIT_DEBIT\","
                        + "\"STATUS\": \"SUCCESS\","
                        + "\"BALANCE\": \"" + balance + "\","
                        + "\"UID\": \"" + uid + "\","
                        + "\"DEVICE_SERIAL_NUMBER\": \"" + deviceSerialNumber + "\","
                        + "\"MESSAGE\": \"Credit/Debit " + topupAmount + "Transaction Successful\""
                        + "}";

                sendUartBytes(jsonResponse.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Credit/Debit Response : " + jsonResponse);
            }
        } catch (Exception e) {
            Log.e(TAG, "UART send error: " + e.getMessage(), e);
        }
    }

    public void sendSaleSuccessResponse(
            String statusCode,
            String responseType,
            String message,
            String AVALBALANCE,
            String TOP_UP_AMOUNT,
            String tranType,
            String operatorOrderId,
            String MID,
            String TID,
            String baseAmount,
            String RRN,
            String TxnID,
            String ABP_RAW_JSON
    ) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("TRAN_TYPE", tranType);
                json.put("OPERATOR_ORDER_ID", operatorOrderId);
                json.put("RRN", RRN);
                json.put("MID", MID);
                json.put("TID", TID);
                json.put("TxnID", TxnID);
                json.put("AVALBALANCE", AVALBALANCE);
                json.put("TOP_UP_AMOUNT", TOP_UP_AMOUNT);
                json.put("BASE_AMOUNT", baseAmount);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper nested JSON
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Sale Success Response : " + finalJson);

                AppLogger.trxn_log(context, "KIOSK_OUTBOUND", finalJson);

            }
        } catch (Exception e) {
            Log.e(TAG, "UART send error", e);
        }
    }

    public void sendAddMoneySuccessResponse(String responseType,
                                            String statusCode,
                                            String message,
                                            String OperatorOrderId,
                                            String DateTime,
                                            String Last4Digit,
                                            String TID,
                                            String MID,
                                            String RRN,
                                            String TipAmount,
                                            String PosEntryMode,
                                            String ABP_RAW_JSON) {

        deviceSerialNumber = getDeviceSerialNumber();

        try {
            if (uartComm != null) {

                JSONObject json = new JSONObject();

                json.put("STATUS", "SUCCESS");
                json.put("STATUS_CODE", statusCode);
                json.put("RESPONSE_TYPE", responseType);
                json.put("MESSAGE", message);
                json.put("OPERATOR_ORDER_ID", OperatorOrderId);
                json.put("DATE_TIME", DateTime);
                json.put("LAST_4_DIGIT", Last4Digit);
                json.put("RRN", RRN);
                json.put("TID", TID);
                json.put("MID", MID);
                json.put("TIP_AMOUNT", TipAmount);
                json.put("POS_ENTRY_MODE", PosEntryMode);
                json.put("DEVICE_SERIAL_NUMBER", deviceSerialNumber);

                // Proper nested JSON handling
                try {
                    JSONObject abpJson = new JSONObject(ABP_RAW_JSON);
                    json.put("ABP_RAW_JSON", abpJson);
                } catch (Exception ex) {
                    // fallback if invalid JSON
                    json.put("ABP_RAW_JSON", ABP_RAW_JSON);
                }

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Add Money Success Response : " + finalJson);

            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error: " + e.getMessage(), e);
        }
    }

    public void sendSerialNumberResponse(String serialNumber) {

        try {
            Log.d(TAG, "Inside sendSerialNumber");
            Log.d(TAG, "Serial Number: " + serialNumber);

            if (uartComm != null) {

                JSONObject json = new JSONObject();
                json.put("STATUS", "SUCCESS");
                json.put("RESPONSE_TYPE", "SERIAL_NUMBER");
                json.put("SERIAL_NUMBER", serialNumber);

                String finalJson = json.toString();

                sendUartBytes(finalJson.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Send Serial Number Response : " + finalJson);
            }

        } catch (Exception e) {
            Log.e(TAG, "UART send error: " + e.getMessage(), e);
        }
    }

    private void sendUartBytes(byte[] data) throws CommException {
        if (!IntegrationModeStore.isUsb(context)) {
            Log.d(TAG, "skip UART TX — mode=" + IntegrationModeStore.get(context));
            return;
        }
        if (uartComm == null) {
            Log.e(TAG, "uartComm is null — cannot send");
            return;
        }
        uartComm.send(data);
    }

    private String timeStamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}

