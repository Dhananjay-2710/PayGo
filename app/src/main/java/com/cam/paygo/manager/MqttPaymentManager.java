package com.cam.paygo.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.cam.mqtt.MqttLog;
import com.cam.mqtt.MqttManager;
import com.cam.paygo.constants.BankConstants;
import com.cam.paygo.constants.JsonKeys;
import com.cam.paygo.constants.MqttConstants;
import com.cam.paygo.constants.StatusConstants;
import com.cam.paygo.utils.AppLogger;
import com.cam.paygo.utils.MqttPayloadHelper;

import org.json.JSONObject;

import java.util.function.Supplier;

/**
 * MQTT connect / inbound route / pending txn / publish for cloud payment mode.
 * UI and bank launches stay in the Activity via {@link Listener}.
 */
public final class MqttPaymentManager {

    public interface Listener {
        void onCardSaleRequested(String requestId, String amount, String phone, String terminalId);

        void onAirtelQrRequested(String requestId, String amount);

        void onAnyQrDisplay(String requestId, String amount, String orderSn, String datetime, String ctime);

        void onQrPaymentSuccess(String requestId, String amount);

        void onMqttStatusToast(String message);

        void onMqttDebugPopup(String title, String topic, String body);

        boolean isBankBusy();

        boolean isAnyQrScreenVisible();
    }

    private final Context appContext;
    private final Supplier<String> deviceSerialSupplier;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    private Listener listener;
    @Nullable
    private MqttManager mqttManager;

    private boolean txnPending = false;
    private String mqttRequestId = "";
    private String mqttReplyTopic = "";
    private String mqttAmount = "";
    private String mqttPhone = "";
    private String mqttTerminalId = "";

    public MqttPaymentManager(Context context, Supplier<String> deviceSerialSupplier) {
        this.appContext = context.getApplicationContext();
        this.deviceSerialSupplier = deviceSerialSupplier;
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public boolean isTxnPending() {
        return txnPending;
    }

    public void clearPending() {
        txnPending = false;
    }

    public void beginPendingTxn(String requestId, String amount, String phone, String terminalId) {
        txnPending = true;
        mqttRequestId = requestId == null ? "" : requestId;
        mqttAmount = amount == null ? "" : amount;
        mqttPhone = phone == null ? "" : phone;
        mqttTerminalId = terminalId == null ? "" : terminalId;
        mqttReplyTopic = MqttConstants.replyTopic(deviceSerial());
    }

    public void start() {
        if (mqttManager != null && mqttManager.isConnected()) {
            MqttLog.i("MQTT already connected");
            return;
        }
        initConnection();
        MqttLog.i("MQTT channel starting");
    }

    public void stop() {
        try {
            clearPending();
            if (mqttManager != null) {
                mqttManager.disconnect();
                MqttLog.i("MQTT channel stopped");
            }
        } catch (Exception e) {
            MqttLog.e("MQTT stop error", e);
        }
    }

    private void initConnection() {
        mqttManager = MqttManager.getInstance();
        String clientId = deviceSerial();
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = "PayGo_" + System.currentTimeMillis();
        }

        mqttManager.initialize(appContext, MqttConstants.BROKER_URL, clientId);
        mqttManager.setMqttEventListener(new MqttManager.MqttEventListener() {
            @Override
            public void onConnected() {
                MqttLog.d("MQTT: Connected successfully");
                notifyToast("Connected with MQTT");
                if (mqttManager != null) {
                    mqttManager.startStatusUpdate();
                }
            }

            @Override
            public void onConnectionFailed(Throwable exception) {
                MqttLog.e("MQTT: Connection failed", exception);
                notifyToast("MQTT: Connection Failed");
            }

            @Override
            public void onConnectionLost(Throwable cause) {
                MqttLog.e("MQTT: Connection lost", cause);
                notifyToast("MQTT: Connection Lost");
            }

            @Override
            public void onSubscribed(String topic) {
                MqttLog.i("MQTT: Subscriber is listening on: " + topic);
                notifyToast("MQTT listening:\n" + topic);
            }

            @Override
            public void onMessageReceived(String topic, String message) {
                MqttLog.i("MQTT: Message received on topic [" + topic + "]: " + message);
                mainHandler.post(() -> handleInboundMessage(topic, message));
            }
        });

        if (!mqttManager.isConnected()) {
            mqttManager.connect(MqttConstants.USERNAME, MqttConstants.PASSWORD);
        }
    }

    void handleInboundMessage(String topic, String rawMessage) {
        if (!IntegrationModeStore.isCloud(appContext)) {
            MqttLog.d("MQTT: Ignoring inbound — mode=" + IntegrationModeStore.get(appContext));
            return;
        }
        try {
            AppLogger.trxn_log(appContext, BankConstants.MQTT_INBOUND, rawMessage);
            MqttLog.i("MQTT_INBOUND: " + rawMessage);

            JSONObject root = new JSONObject(rawMessage);

            JSONObject pushTo = root.optJSONObject("pushTo");
            if (pushTo != null) {
                String targetDeviceId = pushTo.optString("deviceId", "");
                String serial = deviceSerial();
                if (!targetDeviceId.isEmpty()
                        && serial != null
                        && !targetDeviceId.equals(serial)) {
                    MqttLog.d("MQTT: Ignoring message for other deviceId=" + targetDeviceId);
                    return;
                }
            }

            String type = root.optString("type", root.optString("txnType", "")).trim();
            String requestId = root.optString("request_id",
                    root.optString("externalRefNumber", "")).trim();

            String amountRaw = MqttPayloadHelper.extractAmount(root);
            String amountForBank = MqttPayloadHelper.convertAmountForBank(amountRaw);

            if (MqttPayloadHelper.isQrPaymentSuccess(root, requestId)) {
                MqttLog.i("MQTT: QR payment SUCCESS request_id=" + requestId
                        + " money=" + amountRaw
                        + " amount=" + amountForBank);
                String amount = amountForBank.isEmpty() ? amountRaw : amountForBank;
                Listener l = listener;
                if (l != null) {
                    l.onQrPaymentSuccess(requestId, amount);
                }
                return;
            }

            boolean isQr = "qr".equalsIgnoreCase(type)
                    || (!"card".equalsIgnoreCase(type) && root.has("order_sn"));
            if (type.isEmpty() && isQr) {
                type = "qr";
            }

            MqttLog.i("MQTT: parse type=" + type
                    + " isQr=" + isQr
                    + " hasAmount=" + root.has("amount")
                    + " hasMoney=" + root.has("money")
                    + " hasOrderSn=" + root.has("order_sn")
                    + " amountRaw=[" + amountRaw + "]"
                    + " amountForBank=[" + amountForBank + "]");

            String phone = root.optString("phone",
                    root.optString("customerMobileNumber", "")).trim();
            String terminalId = root.optString("terminalid",
                    root.optString("terminalId", "")).trim();
            String datetime = root.optString("datetime", "").trim();
            String orderSn = root.optString("order_sn",
                    root.optString("orderSn", "")).trim();
            String ctime = "";
            if (root.has("ctime") && !root.isNull("ctime")) {
                ctime = String.valueOf(root.opt("ctime"));
            }

            if (isQr) {
                JSONObject qrNormalized = new JSONObject();
                qrNormalized.put("type", "qr");
                qrNormalized.put("request_id", requestId);
                qrNormalized.put("datetime", datetime);
                qrNormalized.put("money", amountRaw);
                qrNormalized.put("amount", amountForBank);
                qrNormalized.put("order_sn", orderSn);
                qrNormalized.put("ctime", ctime);

                notifyToast("MQTT QR received");
                routeQrRequest(topic, requestId, amountRaw, amountForBank,
                        datetime, orderSn, ctime, qrNormalized);
                return;
            }

            JSONObject normalized = new JSONObject();
            normalized.put("type", type);
            normalized.put("request_id", requestId);
            normalized.put("amount_raw", amountRaw);
            normalized.put("amount", amountForBank);
            normalized.put("phone", phone);
            normalized.put("terminalid", terminalId);

            notifyToast("MQTT " + type + " received");

            if (!"card".equalsIgnoreCase(type)) {
                MqttLog.d("MQTT: Ignoring unsupported txn type=" + type);
                notifyPopup("MQTT Request", topic, MqttPayloadHelper.safePretty(normalized));
                return;
            }

            notifyPopup("MQTT Card Request", topic, MqttPayloadHelper.safePretty(normalized));

            if (requestId.isEmpty() || amountForBank.isEmpty()) {
                MqttLog.e("MQTT: Missing request_id or amount for card sale"
                        + " request_id=[" + requestId + "] amount_raw=[" + amountRaw
                        + "] amount_for_bank=[" + amountForBank + "]");
                notifyToast("MQTT card sale missing request_id/amount");
                return;
            }
            if (txnPending || isBankBusy()) {
                MqttLog.e("MQTT: Sale already in progress, ignoring new request");
                notifyToast("MQTT sale already in progress");
                return;
            }

            MqttLog.i("MQTT: Amount " + amountRaw + " -> bank amount " + amountForBank);
            Listener l = listener;
            if (l != null) {
                l.onCardSaleRequested(requestId, amountForBank, phone, terminalId);
            }
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to handle inbound message", e);
            notifyToast("Invalid MQTT JSON");
        }
    }

    private void routeQrRequest(
            String topic,
            String requestId,
            String amountRaw,
            String amountForBank,
            String datetime,
            String orderSn,
            String ctime,
            JSONObject normalized
    ) {
        MqttLog.i("MQTT: QR request received request_id=" + requestId
                + " money=" + amountRaw
                + " amount=" + amountForBank
                + " datetime=" + datetime
                + " order_sn=" + orderSn
                + " ctime=" + ctime
                + " topic=" + topic
                + " qr_type=" + QrTypeStore.get(appContext));

        if (QrTypeStore.isAirtel(appContext)) {
            String bankAmount = (amountForBank == null || amountForBank.isEmpty()) ? amountRaw : amountForBank;
            if (requestId == null || requestId.trim().isEmpty()
                    || bankAmount == null || bankAmount.trim().isEmpty()) {
                MqttLog.e("MQTT: AIRTEL QR missing request_id or amount");
                notifyToast("MQTT QR missing data");
                notifyPopup("MQTT QR Request", topic, MqttPayloadHelper.safePretty(normalized));
                return;
            }
            if (txnPending || isBankBusy()) {
                MqttLog.e("MQTT: Bank txn already in progress, ignoring AIRTEL QR");
                notifyToast("Payment already in progress");
                return;
            }
            Listener l = listener;
            if (l != null) {
                l.onAirtelQrRequested(requestId.trim(), bankAmount.trim());
            }
            return;
        }

        if (orderSn == null || orderSn.trim().isEmpty()) {
            MqttLog.e("MQTT: QR missing order_sn — cannot open QR screen");
            notifyToast("MQTT QR missing order_sn");
            notifyPopup("MQTT QR Request", topic, MqttPayloadHelper.safePretty(normalized));
            return;
        }

        String amountToShow = (amountForBank == null || amountForBank.isEmpty())
                ? amountRaw
                : amountForBank;
        Listener l = listener;
        if (l != null) {
            l.onAnyQrDisplay(requestId, amountToShow, orderSn.trim(), datetime, ctime);
        }
    }

    public void publishBankResult(boolean success, Intent bankData, String fallbackMessage) {
        if (!IntegrationModeStore.isCloud(appContext) || !txnPending) {
            return;
        }
        String msg = fallbackMessage;
        boolean ok = success;
        if (bankData != null && bankData.getExtras() != null) {
            try {
                String result = bankData.getExtras().getString(JsonKeys.RESULT);
                if (result != null) {
                    String clean = result
                            .replace(BankConstants.STX, "")
                            .replace(BankConstants.ETX, "")
                            .trim();
                    JSONObject root = new JSONObject(clean);
                    msg = root.optString(JsonKeys.STATUS_MSG, fallbackMessage);
                    String statusCode = root.optString(JsonKeys.STATUS_CODE, "");
                    if (!StatusConstants.STATUS_OK.equals(statusCode)) {
                        ok = false;
                    }
                }
            } catch (Exception ignore) {
                // keep fallback
            }
        }
        publishSaleResult(ok, msg, bankData);
    }

    public void publishSaleResult(boolean success, String message, Intent bankData) {
        try {
            JSONObject response = new JSONObject();
            response.put("request_id", mqttRequestId);
            response.put("amount", mqttAmount);
            response.put("phone", mqttPhone);
            response.put("terminalid", mqttTerminalId);
            response.put("status", success ? "SUCCESS" : "FAILED");
            response.put("message", message == null ? "" : message);
            response.put("device_serial", deviceSerial());

            if (bankData != null && bankData.getExtras() != null) {
                String result = bankData.getExtras().getString(JsonKeys.RESULT);
                if (result != null) {
                    String clean = result
                            .replace(BankConstants.STX, "")
                            .replace(BankConstants.ETX, "")
                            .trim();
                    try {
                        response.put("bank_result", new JSONObject(clean));
                    } catch (Exception ignore) {
                        response.put("bank_result_raw", clean);
                    }
                }
            }

            String payload = response.toString();
            AppLogger.trxn_log(appContext, BankConstants.MQTT_OUTBOUND, payload);
            MqttLog.i("MQTT_OUTBOUND: " + payload);

            if (mqttManager != null && mqttManager.isConnected()) {
                boolean published = mqttManager.publish(mqttReplyTopic, payload);
                MqttLog.i("MQTT: Published sale response to " + mqttReplyTopic + " ok=" + published);
                notifyToast(published ? "MQTT response published" : "MQTT publish failed");
            } else {
                MqttLog.e("MQTT: Cannot publish response, client not connected");
            }
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to publish sale response", e);
        } finally {
            txnPending = false;
        }
    }

    private String deviceSerial() {
        try {
            String s = deviceSerialSupplier != null ? deviceSerialSupplier.get() : null;
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isBankBusy() {
        Listener l = listener;
        return l != null && l.isBankBusy();
    }

    private void notifyToast(String message) {
        Listener l = listener;
        if (l != null) {
            mainHandler.post(() -> l.onMqttStatusToast(message));
        }
    }

    private void notifyPopup(String title, String topic, String body) {
        Listener l = listener;
        if (l != null) {
            mainHandler.post(() -> l.onMqttDebugPopup(title, topic, body));
        }
    }
}
