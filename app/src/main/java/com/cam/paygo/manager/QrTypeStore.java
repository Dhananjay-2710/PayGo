package com.cam.paygo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cam.paygo.constants.IntegrationConstants;
import com.cam.paygo.constants.QrTypeConstants;

/**
 * Persists QR routing mode from F8TMS heartbeat (Admin source of truth).
 */
public final class QrTypeStore {

    private static final String TAG = "QrTypeStore";

    private static String cachedType;

    private QrTypeStore() {
    }

    public static String get(Context context) {
        if (cachedType != null && !cachedType.isEmpty()) {
            return cachedType;
        }
        if (context == null) {
            return QrTypeConstants.ANY;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE);
        cachedType = QrTypeConstants.orDefault(
                prefs.getString(QrTypeConstants.KEY_QR_TYPE, QrTypeConstants.ANY));
        return cachedType;
    }

    public static void set(Context context, String type) {
        String normalized = QrTypeConstants.orDefault(type);
        cachedType = normalized;
        if (context == null) {
            Log.w(TAG, "set without context — memory only: " + normalized);
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE)
                .edit()
                .putString(QrTypeConstants.KEY_QR_TYPE, normalized)
                .apply();
        Log.i(TAG, "qr_type saved=" + normalized);
    }

    public static boolean isAirtel(Context context) {
        return QrTypeConstants.AIRTEL.equals(get(context));
    }

    public static boolean isAny(Context context) {
        return QrTypeConstants.ANY.equals(get(context));
    }
}
