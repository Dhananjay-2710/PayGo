package com.cam.paygo.repository;

import com.cam.paygo.api.ApiClient;
import com.cam.paygo.api.ApiConstants;
import com.cam.paygo.model.request.HeartbeatRequest;
import com.cam.paygo.model.response.HeartbeatResponse;

import retrofit2.Callback;

public class HeartbeatRepository {

    public void sendHeartbeat(String token,
                              HeartbeatRequest request,
                              Callback<HeartbeatResponse> callback) {

        ApiClient.getInstance()
                .getApiService()
                .sendHeartbeat(ApiConstants.BEARER_PREFIX + token, request)
                .enqueue(callback);
    }
}
