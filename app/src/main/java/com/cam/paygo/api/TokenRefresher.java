package com.cam.paygo.api;

import android.util.Log;

import androidx.annotation.Nullable;

import com.cam.paygo.manager.AuthManager;
import com.cam.paygo.model.request.RefreshTokenRequest;
import com.cam.paygo.model.response.LoginResponse;

import java.io.IOException;

import retrofit2.Response;

/**
 * Single-flight token refresh. Uses the bare Retrofit client to avoid interceptor loops.
 * f8tms: POST /api/v1/auth/refresh with {"token":"..."}.
 */
public final class TokenRefresher {

    private static final String TAG = "TokenRefresher";
    private static final Object LOCK = new Object();

    private TokenRefresher() {
    }

    /**
     * Force a refresh (scheduled timer or HTTP 401).
     *
     * @return new token, or null on failure
     */
    @Nullable
    public static String refreshBlocking() {
        synchronized (LOCK) {
            String current = AuthManager.getToken();
            if (current == null || current.isEmpty()) {
                Log.w(TAG, "STEP skip — no token to refresh");
                return null;
            }
            return refreshLocked(current);
        }
    }

    @Nullable
    private static String refreshLocked(String current) {
        long remainingMs = AuthManager.getExpiresAtMs() - System.currentTimeMillis();
        Log.i(TAG, "STEP 1/4 refresh request"
                + " remainingMs=" + remainingMs
                + " tokenTail=…" + current.substring(Math.max(0, current.length() - 6)));

        try {
            Log.i(TAG, "STEP 2/4 POST " + ApiConstants.PATH_REFRESH);
            Response<LoginResponse> response = ApiClient.getInstance()
                    .getBareApiService()
                    .refresh(ApiConstants.BEARER_PREFIX + current, new RefreshTokenRequest(current))
                    .execute();

            if (!response.isSuccessful() || response.body() == null
                    || response.body().getData() == null
                    || response.body().getData().getToken() == null
                    || response.body().getData().getToken().isEmpty()) {
                String errBody = null;
                try {
                    if (response.errorBody() != null) {
                        errBody = response.errorBody().string();
                    }
                } catch (Exception ignored) {
                    // ignore
                }
                Log.e(TAG, "STEP 3/4 FAILED HTTP " + response.code()
                        + (errBody != null ? (" body=" + errBody) : ""));
                return null;
            }

            LoginResponse.Data data = response.body().getData();
            long expiresIn = data.resolveExpiresInSeconds();
            String newToken = data.getToken();

            Log.i(TAG, "STEP 3/4 OK"
                    + " validityMins=" + data.getTokenValidityMinutes()
                    + " expiresInSec=" + expiresIn
                    + " newTokenTail=…" + newToken.substring(Math.max(0, newToken.length() - 6)));

            AuthManager.setSession(AuthManager.getAppContext(), newToken, expiresIn);

            Log.i(TAG, "STEP 4/4 session updated"
                    + " newExpiresAtMs=" + AuthManager.getExpiresAtMs()
                    + " (scheduler rescheduled via setSession)");
            return newToken;
        } catch (IOException e) {
            Log.e(TAG, "STEP FAILED network: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "STEP FAILED unexpected: " + e.getMessage(), e);
            return null;
        }
    }
}
