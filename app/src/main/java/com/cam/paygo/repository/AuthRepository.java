package com.cam.paygo.repository;

import com.cam.paygo.api.ApiClient;
import com.cam.paygo.api.ApiConstants;
import com.cam.paygo.model.request.LoginRequest;
import com.cam.paygo.model.request.RefreshTokenRequest;
import com.cam.paygo.model.response.LoginResponse;

import retrofit2.Callback;

public class AuthRepository {

    public void login(LoginRequest request, Callback<LoginResponse> callback) {
        // Bare client: login must not go through auth interceptor / authenticator
        ApiClient.getInstance()
                .getBareApiService()
                .login(request)
                .enqueue(callback);
    }

    public void refresh(String token, Callback<LoginResponse> callback) {
        ApiClient.getInstance()
                .getBareApiService()
                .refresh(ApiConstants.BEARER_PREFIX + token, new RefreshTokenRequest(token))
                .enqueue(callback);
    }
}
