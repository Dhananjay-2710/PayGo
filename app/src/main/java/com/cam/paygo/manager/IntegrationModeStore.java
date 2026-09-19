package com.cam.paygo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.cam.paygo.constants.IntegrationConstants;

/**
 * Persists and exposes the active host integration mode (USB / CLOUD).
 * Server (heartbeat) is source of truth after first sync.
 */
public final class IntegrationModeStore {

    private static final String TAG = "IntegrationModeStore";

    private static String cachedType;

    private IntegrationModeStore() {
    }

    public static String get(Context context) {
        if (cachedType != null && !cachedType.isEmpty()) {
            return cachedType;
        }
        if (context == null) {
            return IntegrationConstants.USB;
        }
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE);
        cachedType = IntegrationConstants.orDefault(
                prefs.getString(IntegrationConstants.KEY_INTEGRATION_TYPE, IntegrationConstants.USB));
        return cachedType;
    }

    public static void set(Context context, String type) {
        String normalized = IntegrationConstants.orDefault(type);
        cachedType = normalized;
        if (context == null) {
            Log.w(TAG, "set without context — memory only: " + normalized);
            return;
        }
        context.getApplicationContext()
                .getSharedPreferences(IntegrationConstants.PREF_DEVICE, Context.MODE_PRIVATE)
                .edit()
                .putString(IntegrationConstants.KEY_INTEGRATION_TYPE, normalized)
                .apply();
        Log.i(TAG, "integration_type saved=" + normalized);
    }

    public static boolean isUsb(Context context) {
        return IntegrationConstants.USB.equals(get(context));
    }

    public static boolean isCloud(Context context) {
        return IntegrationConstants.CLOUD.equals(get(context));
    }
}
