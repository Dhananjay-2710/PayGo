package com.cam.paygo.repository;

import com.cam.paygo.api.ApiClient;
import com.cam.paygo.model.request.LoginRequest;
import com.cam.paygo.model.response.LoginResponse;

import retrofit2.Callback;

public class AuthRepository {
    public void login(LoginRequest request, Callback<LoginResponse> callback) {
        ApiClient.getInstance()
                .getApiService()
                .login(request)
                .enqueue((Callback<LoginResponse>) callback);
    }

}
