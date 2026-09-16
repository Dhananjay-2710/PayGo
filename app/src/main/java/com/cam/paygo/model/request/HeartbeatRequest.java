package com.cam.paygo.model.request;

import com.google.gson.annotations.SerializedName;

public class HeartbeatRequest {

    @SerializedName("serial_number")
    private String serialNumber;

    public HeartbeatRequest(String serialNumber) {
        this.serialNumber = serialNumber;
    }
}
