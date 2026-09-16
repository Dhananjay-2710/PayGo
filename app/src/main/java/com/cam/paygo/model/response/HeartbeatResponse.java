package com.cam.paygo.model.response;

import com.google.gson.annotations.SerializedName;

public class HeartbeatResponse {

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("data")
    private Data data;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Data getData() {
        return data;
    }

    public static class Data {

        @SerializedName("device_id")
        private int deviceId;

        @SerializedName("heartbeat_interval_sec")
        private int heartbeatIntervalSec;

        @SerializedName("next_heartbeat_at")
        private String nextHeartbeatAt;

        @SerializedName("server_time")
        private String serverTime;

        public int getDeviceId() {
            return deviceId;
        }

        public int getHeartbeatIntervalSec() {
            return heartbeatIntervalSec;
        }

        public String getNextHeartbeatAt() {
            return nextHeartbeatAt;
        }

        public String getServerTime() {
            return serverTime;
        }
    }
}