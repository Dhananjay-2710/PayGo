package com.cam.paygo.constants;

public final class TvmErrorCodes {
    private TvmErrorCodes() {
    }

    public static final String BANK_STATUS_01 = "01";
    public static final String BANK_STATUS_1102 = "1102";

    public static final String MSG_ICC_CMD_ERROR = "ICC CMD Error";
    public static final String MSG_SOCKET_CONNECTION_FAILED = "Socket connection failed";
    public static final String MSG_UNABLE_TO_REACH_SERVER = "Unable to reach server";
    public static final String MSG_HSM_CONNECTION_FAILED = "Sorry, something went wrong connecting to HSM";
    public static final String MSG_DATA_ERROR = "Data Error";
    public static final String MSG_DATABASE_FAILURE = "Database Failure";
    public static final String MSG_TRANSACTION_CANCELLED = "Transaction Cancelled";
    public static final String MSG_TIMEOUT = "Timeout";
    public static final String MSG_DECLINE = "Decline";
    public static final String MSG_DECLINE_DUE_TO_NO_SERVICE_FOUND = "Decline Due to no Service Found";
    public static final String MSG_DUPLICATE_TRANSACTION = "Duplicate transaction";
    public static final String MSG_TERMINATE = "Terminate";
    public static  final String MSG_PPSE_ERROR = "PPSE Error";
    public static  final String MSG_DUPLICATE_REQUEST = "Duplicate request";
    public static final String TVM_APB_TIMEOUT = "F8_APB_TIMEOUT";
}
