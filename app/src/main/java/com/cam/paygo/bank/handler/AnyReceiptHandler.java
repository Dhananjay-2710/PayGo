package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.bank.ReceiptData;

public class AnyReceiptHandler implements BankResponseHandler {
    private static final String TAG = "AnyReceiptHandler";
    private final UartManager uartManager;

    public AnyReceiptHandler(UartManager uartManager) {
        this.uartManager = uartManager;
    }

    @Override
    public void handle(BankResponse response) {
        BankHandlerSupport.runSafely(uartManager, response, TAG, () -> {
            String statusCode = response.getStatusCode();
            String responseType = response.getResponseType();
            String message = response.getStatusMessage();
            ReceiptData r = response.getReceiptData();
            String ABP_RAW_JSON = response.getCleanResult();

            // Extract data from ReceiptData
            String IsPinEntered = r.get("IsPinEntered");
            String SwitchMerchantId = r.get("SwitchMerchantId");
            String SwitchTerminalId = r.get("SwitchTerminalId");
            String TxnID = r.get("TxnID");
            String AID = r.get("AID");
            String AppVersion = r.get("AppVersion");
            String AuthCode = r.get("AuthCode");
            String BaseAmount = r.get("BaseAmount");
            String BatchNr = r.get("BatchNr");
            String CrdType = r.get("CrdType");
            String DateTime = r.get("DateTime");
            String EmvAppName = r.get("EmvAppName");
            String InvoiceNr = r.get("InvoiceNr");
            String Last4Digit = r.get("Last4Digit");
            String MID = r.get("MID");
            String operatorRefundId = r.get("operatorRefundId");
            String RRN = r.get("RRN");
            String SaleAmt = r.get("SaleAmt");
            String Stan = r.get("Stan");
            String TID = r.get("TID");
            String TranType = r.get("TranType");

            Log.d(TAG, "AnyReceipt Response Success");
            Log.d(TAG, "IsPinEntered : " + IsPinEntered);
            Log.d(TAG, "SwitchMerchantId : " + SwitchMerchantId);
            Log.d(TAG, "SwitchTerminalId : " + SwitchTerminalId);
            Log.d(TAG, "TxnID : " + TxnID);
            Log.d(TAG, "AID : " + AID);
            Log.d(TAG, "AppVersion : " + AppVersion);
            Log.d(TAG, "AuthCode : " + AuthCode);
            Log.d(TAG, "BaseAmount : " + BaseAmount);
            Log.d(TAG, "BatchNr : " + BatchNr);
            Log.d(TAG, "CrdType : " + CrdType);
            Log.d(TAG, "DateTime : " + DateTime);
            Log.d(TAG, "EmvAppName : " + EmvAppName);
            Log.d(TAG, "InvoiceNr : " + InvoiceNr);
            Log.d(TAG, "Last4Digit : " + Last4Digit);
            Log.d(TAG, "MID : " + MID);
            Log.d(TAG, "operatorRefundId : " + operatorRefundId);
            Log.d(TAG, "RRN : " + RRN);
            Log.d(TAG, "TID : " + TID);
            Log.d(TAG, "SaleAmt : " + SaleAmt);
            Log.d(TAG, "TipAmtTypeStan : " + Stan);
            Log.d(TAG, "TranType : " + TranType);

            // Call Response Handler
            uartManager.sendAnyReceiptResponse(
                    statusCode,
                    responseType,
                    message,
                    TranType,
                    TxnID,
                    CrdType,
                    BaseAmount,
                    ABP_RAW_JSON
            );
        });
    }
}
