package com.cam.paygo.api;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static ApiClient instance;

    private final ApiService apiService;
    private final ApiService bareApiService;
    private final OkHttpClient authedOkHttpClient;

    private ApiClient() {
        OkHttpClient bareClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        bareApiService = new Retrofit.Builder()
                .baseUrl(ApiConstants.BASE_URL)
                .client(bareClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);

        authedOkHttpClient = bareClient.newBuilder()
                .addInterceptor(new AuthInterceptor())
                .authenticator(new TokenAuthenticator())
                .build();

        apiService = new Retrofit.Builder()
                .baseUrl(ApiConstants.BASE_URL)
                .client(authedOkHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    /** Login / refresh only — no auth interceptor (avoids refresh loops). */
    public ApiService getBareApiService() {
        return bareApiService;
    }

    public OkHttpClient getAuthedOkHttpClient() {
        return authedOkHttpClient;
    }
}
