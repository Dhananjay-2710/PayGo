package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.bank.ReceiptData;

public class MoneyLoadByCashHandler implements BankResponseHandler {
    private static final String TAG = "MoneyLoadByCashHandler";
    private final UartManager uartManager;

    public MoneyLoadByCashHandler(UartManager uartManager) {
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
            String DateTime = r.get("DateTime");
            String EmvAppName = r.get("EmvAppName");
            String InvoiceNr = r.get("InvoiceNr");
            String Last4Digit = r.get("Last4Digit");
            String MID = r.get("MID");
            String NameOnCard = r.get("NameOnCard");
            String OperatorOrderId = r.get("operatorOrderId");
            String RRN = r.get("RRN");
            String Stan = r.get("Stan");
            String TID = r.get("TID");
            String TipAmount = r.get("TipAmount");
            String TipAmtType = r.get("TipAmtType");
            String TranType = r.get("TranType");
            String TSI = r.get("TSI");
            String TVR = r.get("TVR");
            String Type = r.get("type");

            // Print the required data
            Log.d(TAG, "Money Load By Cash Response Success");
            Log.d(TAG, "IsPinEntered : " + IsPinEntered);
            Log.d(TAG, "PosEntryMode : " + PosEntryMode);
            Log.d(TAG, "SMID : " + SMID);
            Log.d(TAG, "SwitchMerchantId : " + SwitchMerchantId);
            Log.d(TAG, "SwitchTerminalId : " + SwitchTerminalId);
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
            Log.d(TAG, "DateTime : " + DateTime);
            Log.d(TAG, "EmvAppName : " + EmvAppName);
            Log.d(TAG, "InvoiceNr : " + InvoiceNr);
            Log.d(TAG, "Last4Digit : " + Last4Digit);
            Log.d(TAG, "MID : " + MID);
            Log.d(TAG, "NameOnCard : " + NameOnCard);
            Log.d(TAG, "OperatorOrderId : " + OperatorOrderId);
            Log.d(TAG, "RRN : " + RRN);
            Log.d(TAG, "Stan : " + Stan);
            Log.d(TAG, "TID : " + TID);
            Log.d(TAG, "TipAmount : " + TipAmount);
            Log.d(TAG, "TipAmtType : " + TipAmtType);
            Log.d(TAG, "TranType : " + TranType);
            Log.d(TAG, "TSI : " + TSI);
            Log.d(TAG, "TVR : " + TVR);
            Log.d(TAG, "type : " + Type);

            // Call Response Handler
            uartManager.sendAddMoneySuccessResponse(
                    statusCode,
                    responseType,
                    message,
                    OperatorOrderId,
                    DateTime,
                    Last4Digit,
                    TID,
                    MID,
                    RRN,
                    TipAmount,
                    PosEntryMode,
                    ABP_RAW_JSON
            );
        });
    }
}
