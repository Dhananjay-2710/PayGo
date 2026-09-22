package com.cam.paygo.constants;

public final class MqttConstants {
    private MqttConstants() {
    }

    public static final String BROKER_HOST = "demo.ctrmv.com";
    public static final String BROKER_PORT = "1883";
    public static final String BROKER_URL = "tcp://" + BROKER_HOST + ":" + BROKER_PORT;

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    /** Device reply topic: mqtt/{deviceSerial}/response */
    public static String replyTopic(String deviceSerial) {
        String serial = deviceSerial == null || deviceSerial.trim().isEmpty()
                ? "unknown"
                : deviceSerial.trim();
        return "mqtt/" + serial + "/response";
    }
}
