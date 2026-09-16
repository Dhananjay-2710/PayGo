package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.bank.ReceiptData;

public class VoidHandler implements BankResponseHandler {
    private static final String TAG = "VoidHandler";
    private final UartManager uartManager;

    public VoidHandler(UartManager uartManager) {
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
            String PosEntryMode = r.get("PosEntryMode");
            String SMID = r.get("SMID");
            String SwitchMerchantId = r.get("SwitchMerchantId");
            String SwitchTerminalId = r.get("SwitchTerminalId");
            String TC = r.get("TC");
            String TxnCompletionDate = r.get("TxnCompletionDate");
            String TxnID = r.get("TxnID");
            String TxnInitDate = r.get("TxnInitDate");
            String TxnStatus = r.get("TxnStatus");
            String AID = r.get("AID");
            String AppVersion = r.get("AppVersion");
            String AuthCode = r.get("AuthCode");
            String BaseAmount = r.get("BaseAmount");
            String BatchNr = r.get("BatchNr");
            String ContactType = r.get("ContactType");
            String CrdType = r.get("CrdType");
            String Currency = r.get("Currency");
            String CustCode = r.get("CustCode");
            String DateTime = r.get("DateTime");
            String EmvAppName = r.get("EmvAppName");
            String integrationMode = r.get("integrationMode");
            String InvoiceNr = r.get("InvoiceNr");
            String Last4Digit = r.get("Last4Digit");
            String MID = r.get("MID");
            String NameOnCard = r.get("NameOnCard");
            String operatorOrderId = r.get("operatorOrderId");
            String RRN = r.get("RRN");
            String SaleAmt = r.get("SaleAmt");
            String Stan = r.get("Stan");
            String TID = r.get("TID");
            String TipAmount = r.get("TipAmount");
            String TipAmtType = r.get("TipAmtType");
            String TranType = r.get("TranType");
            String TSI = r.get("TSI");
            String TVR = r.get("TVR");
            String Type = r.get("type");

            // Log the extracted data
            Log.d(TAG, "Void Response Success");
            Log.d(TAG, "IsPinEntered : " + IsPinEntered);
            Log.d(TAG, "PosEntryMode : " + PosEntryMode);
            Log.d(TAG, "SMID : " + SMID);
            Log.d(TAG, "SwitchMerchantId : " + SwitchMerchantId);
            Log.d(TAG, "SwitchTerminalId : " + SwitchTerminalId);
            Log.d(TAG, "TC : " + TC);
            Log.d(TAG, "TxnCompletionDate : " + TxnCompletionDate);
            Log.d(TAG, "TxnID : " + TxnID);
            Log.d(TAG, "TxnInitDate : " + TxnInitDate);
            Log.d(TAG, "TxnStatus : " + TxnStatus);
            Log.d(TAG, "AID : " + AID);
            Log.d(TAG, "AppVersion : " + AppVersion);
            Log.d(TAG, "AuthCode : " + AuthCode);
            Log.d(TAG, "BaseAmount : " + BaseAmount);
            Log.d(TAG, "BatchNr : " + BatchNr);
            Log.d(TAG, "ContactType : " + ContactType);
            Log.d(TAG, "CrdType : " + CrdType);
            Log.d(TAG, "Currency : " + Currency);
            Log.d(TAG, "CustCode : " + CustCode);
            Log.d(TAG, "DateTime : " + DateTime);
            Log.d(TAG, "EmvAppName : " + EmvAppName);
            Log.d(TAG, "integrationMode : " + integrationMode);
            Log.d(TAG, "InvoiceNr : " + InvoiceNr);
            Log.d(TAG, "Last4Digit : " + Last4Digit);
            Log.d(TAG, "MID : " + MID);
            Log.d(TAG, "NameOnCard : " + NameOnCard);
            Log.d(TAG, "operatorOrderId : " + operatorOrderId);
            Log.d(TAG, "RRN : " + RRN);
            Log.d(TAG, "SaleAmt : " + SaleAmt);
            Log.d(TAG, "Stan : " + Stan);
            Log.d(TAG, "TID : " + TID);
            Log.d(TAG, "TipAmount : " + TipAmount);
            Log.d(TAG, "TipAmtType : " + TipAmtType);
            Log.d(TAG, "TranType : " + TranType);
            Log.d(TAG, "TSI : " + TSI);
            Log.d(TAG, "TVR : " + TVR);
            Log.d(TAG, "type : " + Type);

            // Response Handler
            uartManager.sendVoidResponse(
                    statusCode,
                    responseType,
                    message,
                    TranType,
                    operatorOrderId,
                    SaleAmt,
                    RRN,
                    MID,
                    InvoiceNr,
                    TxnID,
                    ABP_RAW_JSON
            );
        });
    }
}
