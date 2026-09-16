package com.cam.paygo.utils;

import com.cam.paygo.bank.ReceiptData;

/**
 * Safe reads from {@link ReceiptData} when receipt JSON is partial or null.
 */
public final class ReceiptExtractor {

    private ReceiptExtractor() {
    }

    public static String get(ReceiptData r, String key) {
        return r == null ? "" : r.get(key);
    }

    public static String get(ReceiptData r, String key, String defaultValue) {
        if (r == null) return defaultValue;
        String v = r.get(key);
        return (v == null || v.isEmpty()) ? defaultValue : v;
    }
}
