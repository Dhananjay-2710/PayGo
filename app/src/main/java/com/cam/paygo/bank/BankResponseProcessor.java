package com.cam.paygo.bank;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cam.paygo.constants.StatusConstants;
import com.cam.paygo.constants.TxnConstants;
import com.cam.paygo.manager.UartManager;
import com.cam.paygo.bank.handler.AnyReceiptHandler;
import com.cam.paygo.bank.handler.BalanceEnquiryHandler;
import com.cam.paygo.bank.handler.BalanceUpdateHandler;
import com.cam.paygo.bank.handler.BankResponseHandler;
import com.cam.paygo.bank.handler.MoneyLoadByAccountHandler;
import com.cam.paygo.bank.handler.MoneyLoadByCashHandler;
import com.cam.paygo.bank.handler.RefundHandler;
import com.cam.paygo.bank.handler.SaleHandler;
import com.cam.paygo.bank.handler.ServiceCreationHandler;
import com.cam.paygo.bank.handler.TransactionEnquiryHandler;
import com.cam.paygo.bank.handler.VoidHandler;

import java.util.HashMap;
import java.util.Map;

public class BankResponseProcessor {

    private static final String TAG = "BankResponseProcessor";
    //    private final Context context;
    private final UartManager uartManager;
    private final BankResponseParser parser;
    private final Map<String, BankResponseHandler> handlerMap;

    public BankResponseProcessor(UartManager uartManager, Context ctx) {
//        this.context = ctx;
        this.uartManager = uartManager;
        this.parser = new BankResponseParser(ctx);
        this.handlerMap = new HashMap<>();

        registerHandlers();

    }

    private void registerHandlers() {
        handlerMap.put(TxnConstants.SALE, new SaleHandler(uartManager));
        handlerMap.put(TxnConstants.BALANCE_ENQUIRY, new BalanceEnquiryHandler(uartManager));
        handlerMap.put(TxnConstants.BALANCE_UPDATE, new BalanceUpdateHandler(uartManager));
        handlerMap.put(TxnConstants.MONEY_LOAD_BY_ACCOUNT, new MoneyLoadByAccountHandler(uartManager));
        handlerMap.put(TxnConstants.MONEY_LOAD_BY_CASH, new MoneyLoadByCashHandler(uartManager));
        handlerMap.put(TxnConstants.VOID, new VoidHandler(uartManager));
        handlerMap.put(TxnConstants.SERVICE_CREATION, new ServiceCreationHandler(uartManager));
        handlerMap.put(TxnConstants.TRANSACTION_ENQUIRY, new TransactionEnquiryHandler(uartManager));
        handlerMap.put(TxnConstants.REFUND, new RefundHandler(uartManager));
        handlerMap.put(TxnConstants.ANY_RECEIPT, new AnyReceiptHandler(uartManager));
    }

    public void process(Intent data) {
        try {
            BankResponse response = parser.parse(data);
            Log.d(TAG, "Status Code form the Bank Response : " + response.getStatusCode());
            if (!StatusConstants.STATUS_OK.equals(response.getStatusCode())) {
                uartManager.sendErrorResponse(
                        response.getResponseType(),
                        response.getStatusCode(),
                        response.getStatusMessage(),
                        response.getErpTranId(),
                        response.getCleanResult()
                );
                return;
            }

            BankResponseHandler handler =
                    handlerMap.get(response.getResponseType().toUpperCase());

            if (handler != null) {
                handler.handle(response);

            } else {
                uartManager.sendErrorResponse(
                        response.getResponseType(),
                        response.getStatusCode(),
                        "Unsupported Response Type",
                        response.getErpTranId(),
                        response.getCleanResult()
                );
            }

        } catch (Exception e) {
            Log.e(TAG, "Bank response processing failed", e);
            String detail = e.getClass().getSimpleName() + ": " + e.getMessage();
            uartManager.sendErrorResponse(
                    StatusConstants.ERROR,
                    StatusConstants.FAILED,
                    detail,
                    "",
                    "");
        }
    }
}