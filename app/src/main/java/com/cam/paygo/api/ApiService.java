package com.cam.paygo.api;

import com.cam.paygo.model.request.DeviceRequest;
import com.cam.paygo.model.request.HeartbeatRequest;
import com.cam.paygo.model.request.LoginRequest;
import com.cam.paygo.model.request.RefreshTokenRequest;
import com.cam.paygo.model.response.DeviceResponse;
import com.cam.paygo.model.response.HeartbeatResponse;
import com.cam.paygo.model.response.LoginResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {

    @POST(ApiConstants.PATH_LOGIN)
    Call<LoginResponse> login(@Body LoginRequest request);

    /**
     * f8tms: POST /api/v1/auth/refresh with body {"token":"..."} (Bearer optional).
     */
    @POST(ApiConstants.PATH_REFRESH)
    Call<LoginResponse> refresh(
            @Header(ApiConstants.HEADER_AUTHORIZATION) String authorization,
            @Body RefreshTokenRequest request);

    @POST(ApiConstants.PATH_REGISTER_DEVICE)
    Call<DeviceResponse> registerDevice(
            @Header(ApiConstants.HEADER_AUTHORIZATION) String token,
            @Body DeviceRequest request);

    @POST(ApiConstants.PATH_HEARTBEAT)
    Call<HeartbeatResponse> sendHeartbeat(
            @Header(ApiConstants.HEADER_AUTHORIZATION) String token,
            @Body HeartbeatRequest request);

}
