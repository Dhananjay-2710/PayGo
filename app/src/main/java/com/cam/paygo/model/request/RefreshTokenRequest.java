package com.cam.paygo.model.request;

import com.google.gson.annotations.SerializedName;

public class RefreshTokenRequest {

    @SerializedName("token")
    private final String token;

    public RefreshTokenRequest(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }
}
