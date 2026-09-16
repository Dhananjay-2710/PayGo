package com.cam.paygo.api;

import com.cam.paygo.BuildConfig;

public class ApiConstants {
    public static final String BASE_URL = BuildConfig.SERVER_URL;
    public static final String EMAIL = BuildConfig.EMAIL;
    public static final String PASSWORD = BuildConfig.PASSWORD;
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    public static final String PATH_LOGIN = "api/v1/auth/login";
    public static final String PATH_REFRESH = "api/v1/auth/refresh";
    public static final String PATH_REGISTER_DEVICE = "api/v1/devices";
    public static final String PATH_HEARTBEAT = "api/v1/devices/heartbeat";
    public static final String PATH_FILE_UPLOAD = "api/v1/logs/upload";
}
