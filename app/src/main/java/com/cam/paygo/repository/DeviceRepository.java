package com.cam.paygo.repository;

import com.cam.paygo.api.ApiClient;
import com.cam.paygo.api.ApiConstants;
import com.cam.paygo.model.request.DeviceRequest;
import com.cam.paygo.model.response.DeviceResponse;

import retrofit2.Callback;

public class DeviceRepository {
    public void registerDevice(String token,
                               DeviceRequest request,
                               Callback<DeviceResponse> callback) {

        ApiClient.getInstance()
                .getApiService()
                .registerDevice(ApiConstants.BEARER_PREFIX + token, request)
                .enqueue(callback);
    }
}
