package com.cam.paygo.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.cam.paygo.manager.AuthManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Schedules a proactive token refresh at {@code expiresAt − leadTime}
 * so refresh always uses a still-valid JWT (does not wait for the next API call).
 */
public final class TokenRefreshScheduler {

    private static final String TAG = "TokenRefreshScheduler";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "token-refresh-scheduler");
        t.setDaemon(true);
        return t;
    });

    private static Runnable pending;
    private static long scheduledFireAtMs;
    private static final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private TokenRefreshScheduler() {
    }

    /** Cancel any pending timer and schedule from the current AuthManager session. */
    public static void reschedule(String reason) {
        cancel(reason);

        long expiresAtMs = AuthManager.getExpiresAtMs();
        if (expiresAtMs <= 0L) {
            Log.i(TAG, "[" + reason + "] skip schedule — no known expiry");
            return;
        }

        String token = AuthManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.i(TAG, "[" + reason + "] skip schedule — no token");
            return;
        }

        long leadMs = AuthManager.refreshLeadMs();
        long fireAtMs = expiresAtMs - leadMs;
        long delayMs = fireAtMs - System.currentTimeMillis();
        if (delayMs < 0L) {
            Log.w(TAG, "[" + reason + "] already inside lead window — refreshing ASAP"
                    + " expiresAt=" + formatTime(expiresAtMs)
                    + " leadMs=" + leadMs);
            delayMs = 0L;
            fireAtMs = System.currentTimeMillis();
        }

        scheduledFireAtMs = fireAtMs;
        final long delayForLog = delayMs;
        pending = () -> {
            Log.i(TAG, "TIMER FIRED — starting scheduled refresh"
                    + " fireAt=" + formatTime(scheduledFireAtMs)
                    + " expiresAt=" + formatTime(AuthManager.getExpiresAtMs())
                    + " tokenTail=" + tokenTail(AuthManager.getToken()));
            runRefreshOnWorker("timer");
        };

        MAIN.postDelayed(pending, delayMs);
        Log.i(TAG, "[" + reason + "] scheduled refresh"
                + " in " + delayForLog + "ms"
                + " fireAt=" + formatTime(fireAtMs)
                + " expiresAt=" + formatTime(expiresAtMs)
                + " leadMs=" + leadMs
                + " tokenTail=" + tokenTail(token));
    }

    public static void cancel(String reason) {
        if (pending != null) {
            MAIN.removeCallbacks(pending);
            Log.i(TAG, "[" + reason + "] cancelled pending refresh"
                    + " wasFireAt=" + formatTime(scheduledFireAtMs));
            pending = null;
            scheduledFireAtMs = 0L;
        }
    }

    private static void runRefreshOnWorker(String reason) {
        if (!refreshInFlight.compareAndSet(false, true)) {
            Log.w(TAG, "[" + reason + "] refresh already in flight — skip");
            return;
        }

        WORKER.execute(() -> {
            try {
                long remainingMs = AuthManager.getExpiresAtMs() - System.currentTimeMillis();
                Log.i(TAG, "[" + reason + "] refresh start"
                        + " remainingMs=" + remainingMs
                        + " tokenTail=" + tokenTail(AuthManager.getToken()));

                if (AuthManager.getToken() == null || AuthManager.getToken().isEmpty()) {
                    Log.e(TAG, "[" + reason + "] no token available — invalidate");
                    AuthManager.invalidateSession(AuthManager.getAppContext());
                    return;
                }

                if (remainingMs <= 0L) {
                    Log.e(TAG, "[" + reason + "] token already expired before refresh"
                            + " — refresh may fail; attempting anyway");
                }

                String newToken = TokenRefresher.refreshBlocking();
                if (newToken == null || newToken.isEmpty()) {
                    Log.e(TAG, "[" + reason + "] scheduled refresh FAILED — invalidate + re-login");
                    AuthManager.invalidateSession(AuthManager.getAppContext());
                } else {
                    Log.i(TAG, "[" + reason + "] scheduled refresh SUCCESS"
                            + " newTokenTail=" + tokenTail(newToken)
                            + " newExpiresAt=" + formatTime(AuthManager.getExpiresAtMs())
                            + " (next timer scheduled by setSession)");
                }
            } finally {
                refreshInFlight.set(false);
            }
        });
    }

    private static String formatTime(long epochMs) {
        if (epochMs <= 0L) {
            return "n/a";
        }
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date(epochMs));
    }

    private static String tokenTail(String token) {
        if (token == null || token.isEmpty()) {
            return "none";
        }
        int n = Math.min(6, token.length());
        return "…" + token.substring(token.length() - n);
    }
}
