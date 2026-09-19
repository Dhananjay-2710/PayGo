package com.cam.paygo.bank;

import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import com.cam.paygo.BuildConfig;
import com.cam.paygo.constants.AppConstants;
import com.cam.paygo.utils.AppLogger;
import com.cam.paygo.constants.BankConstants;
import com.cam.paygo.constants.JsonKeys;
import com.cam.paygo.constants.TxnConstants;
import java.util.concurrent.atomic.AtomicInteger;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class ABPBank {

    private static final String TAG = "ABPBank";

    private final Context context;

    private final String IM30intent = BankConstants.BANK_PACKAGE_IM30;
    private final String A910Sintent = BankConstants.BANK_PACKAGE_A910S;

    private static final int MAX_PART_LEN = 12;
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    public ABPBank(Context context) {
        this.context = context;
    }

    // =====================================================
    // 🔥 MAIN METHOD
    // =====================================================

    public Intent createBankAppIntent(
            String tranType,
            String amount,
            String sourceTxnId,
            String dbTxnId,
            String operatorOrderId,
            String invoiceNo,
            String rrn,
            String IS_OFFLINE,
            String PRINT_FLAG,
            String SHIFT_NO,
            String STATION_NAME,
            String STATION_ID,
            String GATENO,
            String UDF1,
            String UDF2,
            String UDF3,
            String UDF4,
            String UDF5
    ) {
        Log.d(TAG, "Processing: " + tranType);
        if (BuildConfig.ENABLE_TEST_ORDER_ID) {
            if (Objects.equals(operatorOrderId, "")) {
                Log.d(TAG, "Generating new operatorOrderId");
                operatorOrderId = generateBuTxnId("prod", getDeviceSerialNumber(), AppConstants.UNKNOWN_COMMUTER, System.currentTimeMillis());
            }
        }

        if (Objects.equals(UDF3, "")) {
            UDF3 = getDeviceSerialNumber();
        }
        try {
            JSONObject json = buildJson(
                    tranType,
                    amount,
                    sourceTxnId,
                    dbTxnId,
                    operatorOrderId,
                    invoiceNo,
                    rrn,
                    IS_OFFLINE,
                    PRINT_FLAG,
                    SHIFT_NO,
                    STATION_NAME,
                    STATION_ID,
                    GATENO,
                    UDF1,
                    UDF2,
                    UDF3,
                    UDF4,
                    UDF5
            );

            if (json == null) return null;

            Intent intent = getBankIntent();
            if (intent == null) return null;

            String wrapped = wrap(json);
            String code = getRequestCode(tranType);

            AppLogger.trxn_log(context, BankConstants.BANK_OUTBOUND, wrapped);
            attachIntent(intent, tranType, wrapped, code);

            Log.d(TAG, "Final Request: " + wrapped);

            return intent;

        } catch (Exception e) {
            Log.e(TAG, "Error creating bank intent", e);
            return null;
        }
    }

    public String generateBuTxnId(String environment, String tvmId, String commuterId, long nowMillis) {
        return generateTxnId("BU", environment, tvmId, commuterId, nowMillis);
    }

    public String generateTxnId(String type, String environment, String tvmId, String commuterId, long nowMillis) {
            String envPart = normalize(environment, AppConstants.TXN_ENV_PROD);
            String tvmPart = normalize(tvmId, getDeviceSerialNumber());
            String commuterPart = normalize(commuterId, AppConstants.UNKNOWN_COMMUTER);
            String typePart = normalize(type, "GEN");
            String base36RefID = getBase36RefID(timestamp(nowMillis));
            String seqPart = String.format(Locale.US, "%03d", Math.floorMod(SEQ.incrementAndGet(), 1000));
            return envPart +typePart+ tvmPart +base36RefID;
    }

    public static String getBase36RefID(String decForm) {
//      decForm=  new DecimalFormat("00").format(new Random().nextInt(99))+decForm;
            String base36 = Long.toString(new Long(decForm), 36);

            return base36;
    }

    public String normalize(String value, String fallback) {
            String source = value;
            if (source == null || source.trim().isEmpty()) {
                source = fallback;
            }
            String normalized = source.trim()
                    .replaceAll("[^A-Za-z0-9]", "")
                    .toUpperCase(Locale.US);
            if (normalized.isEmpty()) {
                normalized = fallback;
            }
            if (normalized.length() > MAX_PART_LEN) {
                normalized = normalized.substring(0, MAX_PART_LEN);
            }
            return normalized;
        }
        private String timestamp(long nowMillis) {
        return new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(new Date(nowMillis));
    }
//    private String generateOperatorOrderId() {
//        // Step 1: Current timestamp (yyyyMMddHHmmss)
//        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
//                .format(new Date());
//
//        // Step 2: Add milliseconds (last 3 digits)
//        String milliSeconds = String.valueOf(System.currentTimeMillis()).substring(10);
//
//        // Step 3: Optional extra random (to avoid collision)
//        int random = new Random().nextInt(900) + 100; // 3-digit random
//
//        return timeStamp + milliSeconds + random;
//    }

    // =====================================================
    // 🔥 JSON BUILDER (ONLY BUSINESS LOGIC HERE)
    // =====================================================

    private JSONObject buildJson(
            String type,
            String amount,
            String sourceTxnId,
            String dbTxnId,
            String operatorOrderId,
            String invoiceNo,
            String rrn,
            String IS_OFFLINE,
            String PRINT_FLAG,
            String SHIFT_NO,
            String STATION_NAME,
            String STATION_ID,
            String GATENO,
            String UDF1,
            String UDF2,
            String UDF3,
            String UDF4,
            String UDF5
    ) {
        try {
            JSONObject json = new JSONObject();

            switch (type) {

                case TxnConstants.SALE:
                case TxnConstants.CREDIT_DEBIT:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.SALE);
                    json.put(JsonKeys.AMOUNT, amount);
                    json.put(JsonKeys.IS_OFFLINE, IS_OFFLINE);
                    break;

                case TxnConstants.BALANCE_ENQUIRY:
                case TxnConstants.BALANCE_ENQ:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.BALANCE_ENQUIRY);
                    break;

                case TxnConstants.BALANCE_UPDATE:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.BALANCE_UPDATE);
                    break;

                case TxnConstants.MONEY_LOAD_BY_ACCOUNT:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.MONEY_LOAD_BY_ACCOUNT);
                    json.put(JsonKeys.AMOUNT, amount);
                    json.put(JsonKeys.SOURCETXN_ID, sourceTxnId);
                    break;

                case TxnConstants.TOPUP:
                case TxnConstants.MONEY_LOAD_BY_CASH:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.MONEY_LOAD_BY_CASH);
                    json.put(JsonKeys.AMOUNT, amount);
                    json.put(JsonKeys.SOURCETXN_ID, sourceTxnId);
                    break;

                case TxnConstants.VOID:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.VOID);
                    json.put(JsonKeys.INVOICE_NO, invoiceNo);
                    break;

                case TxnConstants.SERVICE_CREATION:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.SERVICE_CREATION);
                    break;

                case TxnConstants.TRANSACTION_ENQUIRY:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.TRANSACTION_ENQUIRY);
                    break;

                case TxnConstants.REFUND:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.REFUND);
                    json.put(JsonKeys.RRN, rrn);
                    json.put(JsonKeys.REQUEST_ID, operatorOrderId);
                    break;

                case TxnConstants.ANY_RECEIPT:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.ANY_RECEIPT);
                    json.put(JsonKeys.INVOICE_NO, invoiceNo);
                    break;

                case TxnConstants.QR:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.QR);
                    json.put(JsonKeys.AMOUNT, amount);
                    break;

                case TxnConstants.BQRANYRECEIPT:
                    json.put(JsonKeys.TRAN_TYPE, TxnConstants.BQRANYRECEIPT);
                    json.put(JsonKeys.RRN, rrn);
                    break;

                default:
                    Log.e(TAG, "Unknown type: " + type);
                    return null;
            }

            // 🔹 COMMON FIELDS (centralized)
            json.put(JsonKeys.OPERATOR_ORDER_ID, operatorOrderId);
            json.put(JsonKeys.SHIFT_NO, SHIFT_NO);
            json.put(JsonKeys.PRINT_FLAG, PRINT_FLAG);
            json.put(JsonKeys.STATION_NAME, STATION_NAME);
            json.put(JsonKeys.STATION_ID, STATION_ID);
            json.put(JsonKeys.GATENO, GATENO);
            json.put(JsonKeys.UDF1, UDF1);
            json.put(JsonKeys.UDF2, UDF2);
            json.put(JsonKeys.UDF3, UDF3);
            json.put(JsonKeys.UDF4, UDF4);
            json.put(JsonKeys.UDF5, UDF5);

            return json;

        } catch (Exception e) {
            Log.e(TAG, "JSON Build Error", e);
            return null;
        }
    }

    // =====================================================
    // 🔥 INTENT BUILDER
    // =====================================================

    private Intent getBankIntent() {

        String model = Build.MODEL;

        String packageName;

        switch (model) {
            case BankConstants.DEVICE_MODEL_IM30:
                packageName = IM30intent;
                break;

            case BankConstants.DEVICE_MODEL_A910S:
                packageName = A910Sintent;
                break;

            default:
                Log.e(TAG, "Unsupported Device: " + model);
                return null;
        }

        return context.getPackageManager().getLaunchIntentForPackage(packageName);
    }

    // =====================================================
    // 🔥 HELPERS
    // =====================================================

    private String wrap(JSONObject json) {
        return BankConstants.STX + json + BankConstants.ETX;
    }

    private void attachIntent(Intent intent, String type, String data, String code) {
        intent.setFlags(0);
        intent.putExtra(JsonKeys.REQUEST_TYPE, type);
        intent.putExtra(JsonKeys.DATA, data);
        intent.putExtra(JsonKeys.INTENT_REQUEST_CODE, code);
    }

    private String getRequestCode(String type) {
        switch (type) {
            case TxnConstants.SALE:
            case TxnConstants.CREDIT_DEBIT:
                return TxnConstants.REQUEST_CODE_SALE;
            case TxnConstants.VOID:
                return TxnConstants.REQUEST_CODE_VOID;
            case TxnConstants.BALANCE_UPDATE:
                return TxnConstants.REQUEST_CODE_BALANCE_UPDATE;
            case TxnConstants.BALANCE_ENQ:
            case TxnConstants.BALANCE_ENQUIRY:
                return TxnConstants.REQUEST_CODE_BALANCE_ENQUIRY;
            case TxnConstants.TOPUP:
                return TxnConstants.REQUEST_CODE_TOPUP;
            case TxnConstants.MONEY_LOAD_BY_ACCOUNT:
                return TxnConstants.REQUEST_CODE_MONEY_LOAD_BY_ACCOUNT;
            case TxnConstants.SERVICE_CREATION:
                return TxnConstants.REQUEST_CODE_SERVICE_CREATION;
            case TxnConstants.TRANSACTION_ENQUIRY:
                return TxnConstants.REQUEST_CODE_TRANSACTION_ENQUIRY;
            case TxnConstants.REFUND:
                return TxnConstants.REQUEST_CODE_REFUND;
            case TxnConstants.ANY_RECEIPT:
                return TxnConstants.REQUEST_CODE_ANY_RECEIPT;
            case TxnConstants.QR:
                return TxnConstants.REQUEST_CODE_QR;
            case TxnConstants.BQRANYRECEIPT:
                return TxnConstants.REQUEST_CODE_BQRRECEIPT;
            default:
                return TxnConstants.REQUEST_CODE_DEFAULT;
        }
    }
}