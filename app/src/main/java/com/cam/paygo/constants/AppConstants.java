package com.cam.paygo.constants;

public final class AppConstants {
    private AppConstants() {
    }

    public static final long REQUEST_TIMEOUT_MS = 65_000L;
    /** Extra wait after payment timeout while bank ActivityResult is still pending (65s + 5s = 70s PAX; 5s UART buffer before TVM 75s). */
    public static final long PAYMENT_GRACE_TIMEOUT_MS = 5_000L;
    public static final long ENQUIRY_TIMEOUT_MS = 10_000L;
    public static final long TAP_CARD_TIMEOUT_MS = 40_000L;

    public static final String PREF_ADMIN = "admin_prefs";
    public static final String KEY_ADMIN_PASSWORD = "admin_password";
    public static final String DEFAULT_ADMIN_PASSWORD = "22042021";
    public static final String PREF_AUTH = "auth_pref";
    public static final String KEY_AUTH_TOKEN = "auth_token";

    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String HEARTBEAT_ERROR = "HEARTBEAT_ERROR";

    public static final String RELEASE_DATE = "04/09/2026";

    public static final String TXN_ENV_PROD = "debug";
    public static final String UNKNOWN_COMMUTER = "Unknown Commuter";
}
