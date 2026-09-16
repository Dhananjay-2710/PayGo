package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.bank.ReceiptData;

public class RefundHandler implements BankResponseHandler {
    private static final String TAG = "RefundHandler";
    private final UartManager uartManager;

    public RefundHandler(UartManager uartManager) {
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
            String AMOUNT = r.get("AMOUNT");
            String MERCHANT_ID = r.get("MERCHANT_ID");
            String SwitchMerchantId = r.get("SwitchMerchantId");
            String SwitchTerminalId = r.get("SwitchTerminalId");
            String TERMINAL_ID = r.get("TERMINAL_ID");
            String TRANSACTION = r.get("TRANSACTION");
            String operatorOrderId = r.get("operatorOrderId");
            String operatorRefundId = r.get("operatorRefundId");
            String RRN = r.get("RRN");

            // Log the extracted data
            Log.d(TAG, "Refund Response Success");
            Log.d(TAG, "AMOUNT : " + AMOUNT);
            Log.d(TAG, "MERCHANT_ID : " + MERCHANT_ID);
            Log.d(TAG, "SwitchMerchantId : " + SwitchMerchantId);
            Log.d(TAG, "SwitchTerminalId : " + SwitchTerminalId);
            Log.d(TAG, "TERMINAL_ID : " + TERMINAL_ID);
            Log.d(TAG, "TRANSACTION : " + TRANSACTION);
            Log.d(TAG, "operatorRefundId : " + operatorRefundId);
            Log.d(TAG, "operatorOrderId : " + operatorOrderId);
            Log.d(TAG, "RRN : " + RRN);

            // Call Response Handler
            uartManager.sendAnyRefundResponse(
                    operatorOrderId,
                    operatorRefundId,
                    TERMINAL_ID,
                    TRANSACTION,
                    AMOUNT,
                    MERCHANT_ID,
                    ABP_RAW_JSON
            );
        });
    }
}
