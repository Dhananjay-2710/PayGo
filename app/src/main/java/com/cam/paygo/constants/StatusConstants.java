package com.cam.paygo.constants;

public final class StatusConstants {
    private StatusConstants() {
    }

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String ERROR = "ERROR";
    public static final String APB_TIMEOUT = "APB_TIMEOUT";
    public static final String STATUS_OK = "00";

    public static final String ERR_BANK_INTENT_NULL_CODE = "001";
    public static final String ERR_BANK_INTENT_NULL_LOG = "F8001";
    public static final String ERR_BANK_LAUNCH_FAILED_CODE = "002";
    public static final String ERR_BANK_LAUNCH_FAILED_LOG = "F8002";

    /**
     * UART status code when a bank response handler throws before sending success.
     */
    public static final String ERR_HANDLER_UART = "003";
    public static final String ERR_REQUEST_TIMEOUT_LOG = "Error Request Timeout";
    public static final String ERR_TXN_ENQUIRY_REQUEST_TIMEOUT_LOG = "Error Enquiry Request Timeout";
}
