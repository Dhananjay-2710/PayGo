package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.bank.ReceiptData;
import com.cam.paygo.utils.ReceiptExtractor;

public class BalanceEnquiryHandler implements BankResponseHandler {

    private static final String TAG = "BalanceEnquiryHandler";
    private final UartManager uartManager;

    public BalanceEnquiryHandler(UartManager uartManager) {
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

            Log.d(TAG, r.toString());
            // Extract data from ReceiptData
            String AVALBALANCE = r.get("AVALBALANCE");
            String TOP_UP_AMOUNT = r.get("TOP_UP_AMOUNT");
            String IsPinEntered = r.get("IsPinEntered");
            String SwitchMerchantId = r.get("SwitchMerchantId");
            String SwitchTerminalId = r.get("SwitchTerminalId");
            String AID = r.get("AID");
            String AppVersion = r.get("AppVersion");
            String ContactType = r.get("ContactType");
            String Currency = r.get("Currency");
            String EmvAppName = r.get("EmvAppName");
            String InvoiceNr = r.get("InvoiceNr");
            String MID = r.get("MID");
            String NameOnCard = r.get("NameOnCard");
            String operatorOrderId = r.get("operatorOrderId");
            String TID = r.get("TID");
            String TipAmount = r.get("TipAmount");
            String TSI = r.get("TSI");
            String TVR = r.get("TVR");
            String cardType = r.get("CrdType");
            String last4Digit = r.get("Last4Digit");
            String tranType = r.get("TranType");
            String dateTime = r.get("DateTime");
            String RRN = ReceiptExtractor.get(r, "RRN");

            // Print the required data
            Log.d(TAG, "Balance Enquiry Success");
            Log.d(TAG, "AVALBALANCE : " + AVALBALANCE);
            Log.d(TAG, "TOP_UP_AMOUNT : " + TOP_UP_AMOUNT);
            Log.d(TAG, "Card Type : " + cardType);
            Log.d(TAG, "IsPinEntered : " + IsPinEntered);
            Log.d(TAG, "SwitchTerminalId : " + SwitchTerminalId);
            Log.d(TAG, "Card : " + last4Digit);
            Log.d(TAG, "DateTime : " + dateTime);
            Log.d(TAG, "RRN : " + RRN);
            Log.d(TAG, "TID : " + TID);
            Log.d(TAG, "tranType : " + tranType);
            Log.d(TAG, "TVR : " + TVR);
            Log.d(TAG, "TSI : " + TSI);
            Log.d(TAG, "TipAmount : " + TipAmount);
            Log.d(TAG, "operatorOrderId : " + operatorOrderId);
            Log.d(TAG, "NameOnCard : " + NameOnCard);
            Log.d(TAG, "SwitchMerchantId : " + SwitchMerchantId);
            Log.d(TAG, "AID : " + AID);
            Log.d(TAG, "MID : " + MID);
            Log.d(TAG, "AppVersion : " + AppVersion);
            Log.d(TAG, "ContactType : " + ContactType);
            Log.d(TAG, "Currency : " + Currency);
            Log.d(TAG, "EmvAppName : " + EmvAppName);
            Log.d(TAG, "InvoiceNr : " + InvoiceNr);

            // Call Response Handler
            uartManager.sendBalanceEnquiryResponse(
                    responseType,
                    statusCode,
                    message,
                    AVALBALANCE,
                    TOP_UP_AMOUNT,
                    cardType,
                    last4Digit,
                    dateTime,
                    RRN,
                    TID,
                    MID,
                    tranType,
                    operatorOrderId,
                    AID,
                    TipAmount,
                    ABP_RAW_JSON
            );
        });
    }
}