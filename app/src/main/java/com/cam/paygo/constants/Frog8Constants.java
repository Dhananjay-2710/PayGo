package com.cam.paygo.constants;

/**
 * Frog8 / Frog8 APP_TO_APP (acquirer=FROG8, MQTT card Sale).
 * See app/docs/App_to_App_Integration_Guide.md
 */
public final class Frog8Constants {
    private Frog8Constants() {
    }

    /** Replace with the real installed Frog8 package before device UAT. */
    public static final String PACKAGE_NAME = "com.newland.template";

    public static final String ACTION_PAYMENT = "android.intent.action.SHUKRIA.PAYMENT";

    public static final String TRANS_TYPE_SALE = "Sale";

    public static final String EXTRA_TRANS_TYPE = "transType";
    public static final String EXTRA_AMOUNT = "amount";
    public static final String EXTRA_TIP = "tip";
    public static final String EXTRA_OUT_ORDER_NO = "outOrderNo";
    public static final String EXTRA_REMARK = "remark";

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_MID = "mid";
    public static final String EXTRA_TID = "tid";
    public static final String EXTRA_CARD_NO = "cardNo";
    public static final String EXTRA_TRACE_NO = "traceNo";
    public static final String EXTRA_BATCH_NO = "batchNo";
    public static final String EXTRA_AUTH_CODE = "authCode";
    public static final String EXTRA_REFERENCE_NO = "referenceNo";
    public static final String EXTRA_ORGANIZATION = "organization";
    public static final String EXTRA_BALANCE = "balance";

    /** Transaction successful. */
    public static final String RESULT_OK = "2700";
    /** Transaction failed. */
    public static final String RESULT_FAIL = "2701";
    /** User cancelled. */
    public static final String RESULT_CANCEL = "2702";

    public static final String LOG_OUTBOUND = "FROG8_OUTBOUND";
    public static final String LOG_INBOUND = "FROG8_INBOUND";
}
