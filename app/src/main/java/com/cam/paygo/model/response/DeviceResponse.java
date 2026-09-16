package com.cam.paygo.model.response;

import com.google.gson.annotations.SerializedName;

public class DeviceResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private static String message;

    @SerializedName("data")
    private static Data data;

    public boolean isSuccess() {
        return success;
    }

    public static String getMessage() {
        return message;
    }

    public static Data getData() {
        return data;
    }

    // 🔽 INNER CLASS
    public static class Data {

        @SerializedName("id")
        private int id;

        @SerializedName("serial_number")
        private String serialNumber;

        @SerializedName("model")
        private String model;

        @SerializedName("heartbeat_interval_sec")
        private int heartbeatIntervalSec;

        @SerializedName("status")
        private String status;

        public int getId() {
            return id;
        }

        public String getSerialNumber() {
            return serialNumber;
        }

        public String getModel() {
            return model;
        }

        public int getHeartbeatIntervalSec() {
            return heartbeatIntervalSec;
        }

        public String getStatus() {
            return status;
        }
    }
}
