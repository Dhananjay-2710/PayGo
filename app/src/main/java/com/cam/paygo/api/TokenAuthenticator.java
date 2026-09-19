package com.cam.paygo.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.cam.paygo.manager.AuthManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Backup path: on 401, refresh once and retry. Keep this even with scheduled refresh.
 */
final class TokenAuthenticator implements Authenticator {

    private static final String TAG = "TokenAuthenticator";

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        String path = response.request().url().encodedPath();
        int prior = responseCount(response);

        Log.w(TAG, "401 received path=" + path + " attempt=" + prior);

        if (prior >= 2) {
            Log.e(TAG, "already retried once — give up");
            return null;
        }

        if (AuthInterceptor.isAuthFreePath(path)) {
            Log.d(TAG, "auth-free path — do not refresh");
            return null;
        }

        Log.i(TAG, "reactive refresh after 401…");
        String newToken = TokenRefresher.refreshBlocking();
        if (newToken == null || newToken.isEmpty()) {
            Log.e(TAG, "reactive refresh FAILED — invalidate session");
            AuthManager.invalidateSession(AuthManager.getAppContext());
            return null;
        }

        Log.i(TAG, "reactive refresh OK — retrying " + path
                + " tokenTail=…" + newToken.substring(Math.max(0, newToken.length() - 6)));
        return response.request().newBuilder()
                .header(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_PREFIX + newToken)
                .build();
    }

    private static int responseCount(Response response) {
        int count = 1;
        Response prior = response.priorResponse();
        while (prior != null) {
            count++;
            prior = prior.priorResponse();
        }
        return count;
    }
}
