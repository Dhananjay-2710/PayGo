package com.cam.paygo.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cam.paygo.api.TokenRefreshScheduler;
import com.cam.paygo.constants.AppConstants;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class AuthManager {

    private static final String TAG = "AuthManager";

    public interface SessionListener {
        void onSessionInvalid();
    }

    private static Context appContext;
    private static String token;
    private static long expiresAtMs;
    private static long tokenTtlMs;
    private static SessionListener sessionListener;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AuthManager() {
    }

    public static void init(Context context) {
        if (context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        Log.d(TAG, "init appContext ready");
    }

    public static Context getAppContext() {
        return appContext;
    }

    public static void setSessionListener(SessionListener listener) {
        sessionListener = listener;
        Log.d(TAG, "session listener " + (listener != null ? "registered" : "cleared"));
    }

    public static void setSession(Context context, String newToken, long expiresInSeconds) {
        if (context != null) {
            init(context);
        }
        token = newToken;
        tokenTtlMs = expiresInSeconds > 0 ? expiresInSeconds * 1000L : 0L;
        expiresAtMs = expiresInSeconds > 0
                ? System.currentTimeMillis() + tokenTtlMs
                : 0L;

        Context prefsContext = context != null ? context : appContext;
        if (prefsContext == null) {
            Log.w(TAG, "Session stored in memory only (no context)"
                    + " expiresIn=" + expiresInSeconds
                    + "s tokenTail=" + tokenTail(newToken));
            TokenRefreshScheduler.reschedule("session_memory");
            return;
        }

        SharedPreferences prefs = prefsContext.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(AppConstants.KEY_AUTH_TOKEN, newToken)
                .putLong(AppConstants.KEY_TOKEN_EXPIRES_AT_MS, expiresAtMs)
                .putLong(AppConstants.KEY_TOKEN_TTL_MS, tokenTtlMs)
                .apply();

        Log.i(TAG, "SESSION SAVED"
                + " expiresIn=" + expiresInSeconds + "s"
                + " ttlMs=" + tokenTtlMs
                + " leadMs=" + refreshLeadMs()
                + " expiresAt=" + formatTime(expiresAtMs)
                + " tokenTail=" + tokenTail(newToken));

        TokenRefreshScheduler.reschedule("session_saved");
    }

    /** @deprecated Prefer {@link #setSession(Context, String, long)} so expiry is tracked. */
    @Deprecated
    public static void setToken(Context context, String t) {
        setSession(context, t, 0L);
    }

    public static String getToken(Context context) {
        if (token != null) {
            return token;
        }

        loadFromPrefs(context != null ? context : appContext);
        return token;
    }

    public static String getToken() {
        return getToken(appContext);
    }

    public static long getExpiresAtMs() {
        if (token == null && appContext != null) {
            getToken(appContext);
        }
        return expiresAtMs;
    }

    public static boolean hasToken(Context context) {
        String t = getToken(context);
        return t != null && !t.isEmpty();
    }

    public static boolean hasKnownExpiry() {
        if (token == null && appContext != null) {
            getToken(appContext);
        }
        return expiresAtMs > 0L;
    }

    /**
     * True when remaining life is within the refresh lead window.
     * Prefer {@link TokenRefreshScheduler} for proactive refresh; this is kept for boot checks.
     */
    public static boolean isExpiringSoon() {
        if (token == null && appContext != null) {
            getToken(appContext);
        }
        if (token == null || token.isEmpty()) {
            return true;
        }
        if (expiresAtMs <= 0L) {
            return false;
        }
        boolean soon = System.currentTimeMillis() >= (expiresAtMs - refreshLeadMs());
        Log.d(TAG, "isExpiringSoon=" + soon
                + " remainingMs=" + (expiresAtMs - System.currentTimeMillis())
                + " leadMs=" + refreshLeadMs());
        return soon;
    }

    /**
     * How early to refresh before expiry (default 30s → 5‑min token refreshes at ~4m30s).
     */
    public static long refreshLeadMs() {
        long lead = AppConstants.TOKEN_REFRESH_LEAD_MS;
        if (tokenTtlMs > 0L) {
            long maxLead = Math.max(5_000L, (tokenTtlMs / 2L) - 1_000L);
            lead = Math.min(lead, maxLead);
        }
        return lead;
    }

    public static void clearSession(Context context) {
        Log.i(TAG, "SESSION CLEARED tokenTailWas=" + tokenTail(token));
        token = null;
        expiresAtMs = 0L;
        tokenTtlMs = 0L;

        TokenRefreshScheduler.cancel("session_cleared");

        Context prefsContext = context != null ? context : appContext;
        if (prefsContext != null) {
            SharedPreferences prefs = prefsContext.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
            prefs.edit()
                    .remove(AppConstants.KEY_AUTH_TOKEN)
                    .remove(AppConstants.KEY_TOKEN_EXPIRES_AT_MS)
                    .remove(AppConstants.KEY_TOKEN_TTL_MS)
                    .apply();
        }
    }

    public static void clearToken(Context context) {
        clearSession(context);
    }

    /** Clear session and ask UI to re-login. */
    public static void invalidateSession(Context context) {
        Log.w(TAG, "SESSION INVALIDATED — notifying UI to re-login");
        clearSession(context);
        notifySessionInvalid();
    }

    private static void loadFromPrefs(Context context) {
        if (context == null) {
            return;
        }
        init(context);
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
        token = prefs.getString(AppConstants.KEY_AUTH_TOKEN, null);
        expiresAtMs = prefs.getLong(AppConstants.KEY_TOKEN_EXPIRES_AT_MS, 0L);
        tokenTtlMs = prefs.getLong(AppConstants.KEY_TOKEN_TTL_MS, 0L);
        if (tokenTtlMs <= 0L && expiresAtMs > System.currentTimeMillis()) {
            tokenTtlMs = expiresAtMs - System.currentTimeMillis();
        }
        Log.i(TAG, "SESSION LOADED FROM PREFS"
                + " hasToken=" + (token != null && !token.isEmpty())
                + " expiresAt=" + formatTime(expiresAtMs)
                + " remainingMs=" + (expiresAtMs > 0 ? expiresAtMs - System.currentTimeMillis() : -1)
                + " tokenTail=" + tokenTail(token));

        if (token != null && !token.isEmpty() && expiresAtMs > 0L) {
            TokenRefreshScheduler.reschedule("prefs_loaded");
        }
    }

    private static void notifySessionInvalid() {
        final SessionListener listener = sessionListener;
        if (listener == null) {
            Log.w(TAG, "no session listener registered");
            return;
        }
        MAIN.post(listener::onSessionInvalid);
    }

    private static String formatTime(long epochMs) {
        if (epochMs <= 0L) {
            return "n/a";
        }
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(epochMs));
    }

    private static String tokenTail(String value) {
        if (value == null || value.isEmpty()) {
            return "none";
        }
        int n = Math.min(6, value.length());
        return "…" + value.substring(value.length() - n);
    }
}
