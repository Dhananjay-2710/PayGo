package com.cam.paygo.constants;

public final class TxnConstants {
    private TxnConstants() {
    }

    public static final String SALE = "SALE";
    public static final String CREDIT_DEBIT = "CREDIT_DEBIT";
    public static final String BALANCE_ENQ = "BALANCE_ENQ";
    public static final String BALANCE_ENQUIRY = "BALANCEENQUIRY";
    public static final String BALANCE_UPDATE = "BALANCEUPDATE";
    public static final String TOPUP = "TOPUP";
    public static final String MONEY_LOAD_BY_CASH = "MONEY_LOAD_BY_CASH";
    public static final String MONEY_LOAD_BY_ACCOUNT = "MONEY_LOAD_BY_ACCOUNT";
    public static final String VOID = "VOID";
    public static final String SERVICE_CREATION = "SERVICECREATION";
    public static final String TRANSACTION_ENQUIRY = "TRANSACTION_ENQUIRY";
    public static final String REFUND = "REFUND";
    public static final String ANY_RECEIPT = "ANYRECEIPT";
    public static final String SERIAL_NUMBER = "SERIAL_NUMBER";

    public static final String REQUEST_CODE_SALE = "101";
    public static final String REQUEST_CODE_VOID = "102";
    public static final String REQUEST_CODE_BALANCE_UPDATE = "104";
    public static final String REQUEST_CODE_BALANCE_ENQUIRY = "105";
    public static final String REQUEST_CODE_TOPUP = "106";
    public static final String REQUEST_CODE_MONEY_LOAD_BY_ACCOUNT = "107";
    public static final String REQUEST_CODE_SERVICE_CREATION = "108";
    public static final String REQUEST_CODE_TRANSACTION_ENQUIRY = "110";
    public static final String REQUEST_CODE_REFUND = "111";
    public static final String REQUEST_CODE_ANY_RECEIPT = "112";
    public static final String REQUEST_CODE_DEFAULT = "000";
}
