package com.cam.paygo.frog8;

import android.content.Intent;
import android.os.Bundle;

import com.cam.paygo.constants.Frog8Constants;

/**
 * Parsed Frog8 APP_TO_APP result extras.
 */
public final class Frog8Response {

    private final String resultCode;
    private final String message;
    private final String mid;
    private final String tid;
    private final String cardNo;
    private final String traceNo;
    private final String batchNo;
    private final String authCode;
    private final String referenceNo;
    private final String organization;
    private final long amount;
    private final long tip;
    private final long balance;

    public Frog8Response(
            String resultCode,
            String message,
            String mid,
            String tid,
            String cardNo,
            String traceNo,
            String batchNo,
            String authCode,
            String referenceNo,
            String organization,
            long amount,
            long tip,
            long balance
    ) {
        this.resultCode = resultCode;
        this.message = message;
        this.mid = mid;
        this.tid = tid;
        this.cardNo = cardNo;
        this.traceNo = traceNo;
        this.batchNo = batchNo;
        this.authCode = authCode;
        this.referenceNo = referenceNo;
        this.organization = organization;
        this.amount = amount;
        this.tip = tip;
        this.balance = balance;
    }

    public static Frog8Response fromIntent(Intent data) {
        if (data == null) {
            return empty("No response data");
        }
        Bundle extras = data.getExtras();
        if (extras == null) {
            return empty("No response extras");
        }
        return new Frog8Response(
                stringExtra(extras, Frog8Constants.EXTRA_RESULT_CODE),
                stringExtra(extras, Frog8Constants.EXTRA_MESSAGE),
                stringExtra(extras, Frog8Constants.EXTRA_MID),
                stringExtra(extras, Frog8Constants.EXTRA_TID),
                stringExtra(extras, Frog8Constants.EXTRA_CARD_NO),
                stringExtra(extras, Frog8Constants.EXTRA_TRACE_NO),
                stringExtra(extras, Frog8Constants.EXTRA_BATCH_NO),
                stringExtra(extras, Frog8Constants.EXTRA_AUTH_CODE),
                stringExtra(extras, Frog8Constants.EXTRA_REFERENCE_NO),
                stringExtra(extras, Frog8Constants.EXTRA_ORGANIZATION),
                longExtra(extras, Frog8Constants.EXTRA_AMOUNT),
                longExtra(extras, Frog8Constants.EXTRA_TIP),
                longExtra(extras, Frog8Constants.EXTRA_BALANCE)
        );
    }

    private static Frog8Response empty(String message) {
        return new Frog8Response("", message, "", "", "", "", "", "", "", "", 0L, 0L, 0L);
    }

    private static String stringExtra(Bundle extras, String key) {
        Object v = extras.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static long longExtra(Bundle extras, String key) {
        Object v = extras.get(key);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public boolean isSuccess() {
        return Frog8Constants.RESULT_OK.equals(resultCode)
                || "OK".equalsIgnoreCase(resultCode);
    }

    public boolean isCancel() {
        return Frog8Constants.RESULT_CANCEL.equals(resultCode)
                || "UC".equalsIgnoreCase(resultCode);
    }

    public String getResultCode() {
        return resultCode;
    }

    public String getMessage() {
        return message;
    }

    public String getMid() {
        return mid;
    }

    public String getTid() {
        return tid;
    }

    public String getCardNo() {
        return cardNo;
    }

    public String getTraceNo() {
        return traceNo;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public String getAuthCode() {
        return authCode;
    }

    public String getReferenceNo() {
        return referenceNo;
    }

    public String getOrganization() {
        return organization;
    }

    public long getAmount() {
        return amount;
    }

    public long getTip() {
        return tip;
    }

    public long getBalance() {
        return balance;
    }

    /** Human-readable summary for MQTT / logs. */
    public String summaryMessage() {
        if (isSuccess()) {
            StringBuilder sb = new StringBuilder("Sale completed");
            if (message != null && !message.isEmpty()) {
                sb.append(": ").append(message);
            }
            if (authCode != null && !authCode.isEmpty()) {
                sb.append(" auth=").append(authCode);
            }
            if (traceNo != null && !traceNo.isEmpty()) {
                sb.append(" trace=").append(traceNo);
            }
            return sb.toString();
        }
        if (isCancel()) {
            return (message == null || message.isEmpty()) ? "Payment cancelled" : message;
        }
        if (message != null && !message.isEmpty()) {
            return message;
        }
        if (resultCode == null || resultCode.isEmpty()) {
            return "Frog8 payment failed";
        }
        return "Frog8 payment failed (" + resultCode + ")";
    }
}
