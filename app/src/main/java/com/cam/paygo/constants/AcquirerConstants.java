package com.cam.paygo.constants;

/**
 * Payment acquirer from F8TMS (not qr_type).
 * Values: AIRTEL (bank app intent) | FROG8 (APP_TO_APP intent).
 * MQTT card and QR both branch on this store.
 */
public final class AcquirerConstants {
    private AcquirerConstants() {
    }

    public static final String KEY_ACQUIRER = "acquirer";

    public static final String AIRTEL = "AIRTEL";
    public static final String FROG8 = "FROG8";

    public static String normalize(String raw) {
        if (raw == null) {
            return AIRTEL;
        }
        String v = raw.trim().toUpperCase();
        if (AIRTEL.equals(v)) {
            return AIRTEL;
        }
        if (FROG8.equals(v)) {
            return FROG8;
        }
        return "";
    }

    public static boolean isValid(String raw) {
        String v = normalize(raw);
        return AIRTEL.equals(v) || FROG8.equals(v);
    }

    public static String orDefault(String raw) {
        String v = normalize(raw);
        return v.isEmpty() ? AIRTEL : v;
    }
}
