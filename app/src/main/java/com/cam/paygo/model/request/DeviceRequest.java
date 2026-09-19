package com.cam.paygo.model.request;

import com.cam.paygo.constants.IntegrationConstants;
import com.google.gson.annotations.SerializedName;

public class DeviceRequest {

    @SerializedName("serial_number")
    private String serialNumber;

    @SerializedName("model")
    private String deviceModel;

    @SerializedName("app_version")
    private String appVersion;

    @SerializedName("integration_type")
    private String integrationType;

    public DeviceRequest(String serialNumber, String deviceModel, String appVersion) {
        this(serialNumber, deviceModel, appVersion, IntegrationConstants.USB);
    }

    public DeviceRequest(String serialNumber, String deviceModel, String appVersion, String integrationType) {
        this.serialNumber = serialNumber;
        this.deviceModel = deviceModel;
        this.appVersion = appVersion;
        this.integrationType = IntegrationConstants.orDefault(integrationType);
    }

    public String getDeviceSerialNumber() {
        return serialNumber;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getIntegrationType() {
        return integrationType;
    }
}
