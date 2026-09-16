package com.cam.paygo.bank;

public class BankResponse {

    private final String responseType;
    private final String statusCode;
    private final String statusMessage;
    private final String erpTranId;
    private final ReceiptData receiptData;
    private final String cleanResult;

    public BankResponse(String responseType,
                        String statusCode,
                        String statusMessage,
                        String erpTranId,
                        ReceiptData receiptData,
                        String cleanResult) {
        this.responseType = responseType;
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.erpTranId = erpTranId;
        this.receiptData = receiptData;
        this.cleanResult = cleanResult;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getErpTranId() {
        return erpTranId;
    }

    public ReceiptData getReceiptData() {
        return receiptData;
    }

    public String getCleanResult() {
        return cleanResult;
    }
}