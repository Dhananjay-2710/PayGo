package com.cam.paygo.frog8;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.cam.paygo.constants.Frog8Constants;
import com.cam.paygo.utils.AppLogger;

/**
 * Processes Frog8 APP_TO_APP activity results (mirror of BankResponseProcessor role for MQTT).
 */
public final class Frog8ResponseProcessor {

    private static final String TAG = "Frog8ResponseProcessor";

    public static final class Outcome {
        public final boolean success;
        public final String message;
        public final Frog8Response response;

        public Outcome(boolean success, String message, Frog8Response response) {
            this.success = success;
            this.message = message;
            this.response = response;
        }
    }

    private final Context context;

    public Frog8ResponseProcessor(Context context) {
        this.context = context.getApplicationContext();
    }

    public Outcome process(Intent data) {
        Frog8Response response = Frog8Response.fromIntent(data);
        String summary = response.summaryMessage();
        String logLine = "resultCode=" + response.getResultCode()
                + " success=" + response.isSuccess()
                + " msg=" + summary
                + " auth=" + response.getAuthCode()
                + " trace=" + response.getTraceNo();
        Log.i(TAG, logLine);
        AppLogger.trxn_log(context, Frog8Constants.LOG_INBOUND, logLine);
        return new Outcome(response.isSuccess(), summary, response);
    }
}
