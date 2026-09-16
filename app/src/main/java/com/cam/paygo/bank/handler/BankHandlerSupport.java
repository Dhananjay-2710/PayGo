package com.cam.paygo.bank.handler;

import android.util.Log;

import com.cam.paygo.bank.BankResponse;
import com.cam.paygo.constants.StatusConstants;
import com.cam.paygo.manager.UartManager;

/**
 * Wraps handler bodies so UART failures are reported instead of failing silently.
 */
public final class BankHandlerSupport {

    private static final String TAG = "BankHandler";

    private BankHandlerSupport() {
    }

    public interface HandlerAction {
        void run() throws Exception;
    }

    public static void runSafely(UartManager uart, BankResponse response, String handlerName, HandlerAction action) {
        try {
            action.run();
        } catch (Exception e) {
            Log.e(TAG, handlerName + ": handler failed", e);
            String tranType = response.getResponseType() != null ? response.getResponseType() : StatusConstants.ERROR;
            String erp = response.getErpTranId() != null ? response.getErpTranId() : "";
            String raw = response.getCleanResult() != null ? response.getCleanResult() : "";
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            uart.sendErrorResponse(tranType, StatusConstants.ERR_HANDLER_UART, msg, erp, raw);
        }
    }
}
