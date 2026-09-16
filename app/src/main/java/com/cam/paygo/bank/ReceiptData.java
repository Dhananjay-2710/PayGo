package com.cam.paygo.bank;

import org.json.JSONObject;

public class ReceiptData {

    private final JSONObject raw;
    private static final String TAG = "ReceiptData";

    public ReceiptData(JSONObject raw) {
        this.raw = raw;
    }

    public String get(String key) {
        return raw != null ? raw.optString(key, "") : "";
    }
}