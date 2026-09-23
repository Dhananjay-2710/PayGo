package com.cam.paygo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cam.paygo.constants.AcquirerConstants;
import com.cam.paygo.constants.IntegrationConstants;

/**
 * Persists acquirer mode from F8TMS heartbeat (Admin source of truth).
 */
public final class AcquirerStore {

    private static final String TAG = "AcquirerStore";

    private static String cachedAcquirer;

    private AcquirerStore() {
    }

    public static String get(Context context) {
        if (cachedAcquirer != null && !cachedAcquirer.isEmpty()) {
            return cachedAcquirer;
        }
        if (context == null) {
            return AcquirerConstants.AIRTEL;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE);
        cachedAcquirer = AcquirerConstants.orDefault(
                prefs.getString(AcquirerConstants.KEY_ACQUIRER, AcquirerConstants.AIRTEL));
        return cachedAcquirer;
    }

    public static void set(Context context, String acquirer) {
        String normalized = AcquirerConstants.orDefault(acquirer);
        cachedAcquirer = normalized;
        if (context == null) {
            Log.w(TAG, "set without context — memory only: " + normalized);
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE)
                .edit()
                .putString(AcquirerConstants.KEY_ACQUIRER, normalized)
                .apply();
        Log.i(TAG, "acquirer saved=" + normalized);
    }

    public static boolean isAirtel(Context context) {
        return AcquirerConstants.AIRTEL.equals(get(context));
    }

    public static boolean isFrog8(Context context) {
        return AcquirerConstants.FROG8.equals(get(context));
    }
}
