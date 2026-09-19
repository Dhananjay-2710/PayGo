package com.cam.paygo.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.cam.paygo.manager.AuthManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Attaches the current Bearer token. Proactive refresh is handled by
 * {@link TokenRefreshScheduler} (timer at expiresAt − 30s), not here.
 */
final class AuthInterceptor implements Interceptor {

    private static final String TAG = "AuthInterceptor";

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String path = original.url().encodedPath();

        if (isAuthFreePath(path)) {
            Log.d(TAG, "auth-free path — pass through: " + path);
            return chain.proceed(original);
        }

        String token = AuthManager.getToken();
        if (token == null || token.isEmpty()) {
            Log.w(TAG, "no token for " + path + " — sending without Authorization");
            return chain.proceed(original);
        }

        long remainingMs = AuthManager.getExpiresAtMs() - System.currentTimeMillis();
        Log.d(TAG, "attach Bearer for " + path
                + " remainingMs=" + remainingMs
                + " tokenTail=…" + token.substring(Math.max(0, token.length() - 6)));

        Request authed = original.newBuilder()
                .header(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_PREFIX + token)
                .build();
        return chain.proceed(authed);
    }

    static boolean isAuthFreePath(String path) {
        if (path == null) {
            return false;
        }
        return path.contains("/auth/login") || path.contains("/auth/refresh");
    }
}
