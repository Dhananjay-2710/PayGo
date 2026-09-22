package com.cam.paygo.constants;

/**
 * QR payment routing: ANY (display order_sn QR) vs AIRTEL (bank app QR txn).
 */
public final class QrTypeConstants {
    private QrTypeConstants() {
    }

    // ---- MQTT QR payment screen ----
    /** How long the QR stays valid while waiting for payment MQTT. */
    public static final long QR_PAYMENT_TIMEOUT_MS = 1 * 60_000L; // 5 minutes
    /** How long success/failed result stays on screen before closing. */
    public static final long QR_RESULT_DISPLAY_MS = 5_000L; // 5 seconds
    public static final long QR_TIMER_TICK_MS = 1_000L;
    public static final int QR_BITMAP_SIZE_PX = 720;

    public static final String KEY_QR_TYPE = "qr_type";

    public static final String AIRTEL = "AIRTEL";
    public static final String ANY = "ANY";

    public static String normalize(String raw) {
        if (raw == null) {
            return ANY;
        }
        String v = raw.trim().toUpperCase();
        if (AIRTEL.equals(v)) {
            return AIRTEL;
        }
        if (ANY.equals(v)) {
            return ANY;
        }
        return "";
    }

    public static boolean isValid(String raw) {
        String v = normalize(raw);
        return AIRTEL.equals(v) || ANY.equals(v);
    }

    public static String orDefault(String raw) {
        String v = normalize(raw);
        return v.isEmpty() ? ANY : v;
    }
}
