package com.cam.paygo.model.request;

import com.google.gson.annotations.SerializedName;

public class DeviceRequest {

    @SerializedName("serial_number")
    private String serialNumber;

    @SerializedName("model")
    private String deviceModel;

    public DeviceRequest(String serialNumber, String deviceModel) {
        this.serialNumber = serialNumber;
        this.deviceModel = deviceModel;
    }

    // Getters
    public String getDeviceSerialNumber() {
        return serialNumber;
    }

    public String getDeviceModel() {
        return deviceModel;
    }
}
