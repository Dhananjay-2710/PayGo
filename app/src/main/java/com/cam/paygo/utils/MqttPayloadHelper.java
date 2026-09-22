package com.cam.paygo.utils;

import com.cam.mqtt.MqttLog;

import org.json.JSONObject;

/**
 * Stateless MQTT payload helpers (amount parse, QR success detect, pretty JSON).
 */
public final class MqttPayloadHelper {

    private MqttPayloadHelper() {
    }

    /**
     * Card uses {@code "amount"}, QR uses {@code "money"}.
     * Avoid optString alone — some org.json builds miss non-string numbers.
     */
    public static String extractAmount(JSONObject root) {
        if (root == null) {
            return "";
        }
        try {
            String fromAmount = readNumberOrString(root, "amount");
            if (!fromAmount.isEmpty()) {
                return fromAmount;
            }
            String fromMoney = readNumberOrString(root, "money");
            if (!fromMoney.isEmpty()) {
                return fromMoney;
            }
            return readNumberOrString(root, "amount_raw");
        } catch (Exception e) {
            MqttLog.e("MQTT: Failed to extract amount", e);
            return "";
        }
    }

    public static String readNumberOrString(JSONObject root, String key) {
        try {
            if (root == null || !root.has(key) || root.isNull(key)) {
                return "";
            }
            Object value = root.get(key);
            if (value instanceof Number || value instanceof String) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    return s;
                }
            }
        } catch (Exception ignore) {
            // fall through
        }
        return "";
    }

    /**
     * MQTT amount may be paise-style (₹1 -> 100). Convert to rupees for bank.
     * Values &lt; 100 or non-multiples of 100 are treated as already-rupees.
     */
    public static String convertAmountForBank(String amountRaw) {
        if (amountRaw == null || amountRaw.trim().isEmpty()) {
            return "";
        }
        try {
            java.math.BigDecimal raw = new java.math.BigDecimal(amountRaw.trim());
            if (raw.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return "";
            }
            java.math.BigDecimal bankAmount;
            if (raw.compareTo(new java.math.BigDecimal("100")) >= 0
                    && raw.remainder(new java.math.BigDecimal("100"))
                    .compareTo(java.math.BigDecimal.ZERO) == 0) {
                bankAmount = raw.divide(new java.math.BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP);
            } else {
                bankAmount = raw;
            }
            if (bankAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return "";
            }
            return bankAmount.stripTrailingZeros().toPlainString();
        } catch (Exception e) {
            MqttLog.e("MQTT: Invalid amount: " + amountRaw, e);
            return "";
        }
    }

    /**
     * Success when request_id starts with UN_ and biz_type=1, broadcast_type=1.
     */
    public static boolean isQrPaymentSuccess(JSONObject root, String requestId) {
        if (root == null || requestId == null || !requestId.startsWith("UN_")) {
            return false;
        }
        int bizType = root.optInt("biz_type", -1);
        int broadcastType = root.optInt("broadcast_type", -1);
        if (bizType < 0 && root.has("biz_type")) {
            try {
                bizType = Integer.parseInt(String.valueOf(root.opt("biz_type")).trim());
            } catch (Exception ignore) {
                bizType = -1;
            }
        }
        if (broadcastType < 0 && root.has("broadcast_type")) {
            try {
                broadcastType = Integer.parseInt(String.valueOf(root.opt("broadcast_type")).trim());
            } catch (Exception ignore) {
                broadcastType = -1;
            }
        }
        return bizType == 1 && broadcastType == 1;
    }

    public static String safePretty(JSONObject json) {
        if (json == null) {
            return "(empty)";
        }
        try {
            return json.toString(2);
        } catch (Exception e) {
            return json.toString();
        }
    }
}
