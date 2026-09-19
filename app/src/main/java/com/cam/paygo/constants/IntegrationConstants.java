package com.cam.paygo.constants;

/**
 * Host integration channel: USB (UART) vs CLOUD (MQTT).
 */
public final class IntegrationConstants {
    private IntegrationConstants() {
    }

    public static final String PREF_DEVICE = "device_prefs";
    public static final String KEY_INTEGRATION_TYPE = "integration_type";

    public static final String USB = "USB";
    public static final String CLOUD = "CLOUD";

    public static String normalize(String raw) {
        if (raw == null) {
            return USB;
        }
        String v = raw.trim().toUpperCase();
        if (CLOUD.equals(v)) {
            return CLOUD;
        }
        if (USB.equals(v)) {
            return USB;
        }
        return "";
    }

    public static boolean isValid(String raw) {
        String v = normalize(raw);
        return USB.equals(v) || CLOUD.equals(v);
    }

    public static String orDefault(String raw) {
        String v = normalize(raw);
        return v.isEmpty() ? USB : v;
    }
}
