package com.cam.paygo.manager;

import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.cam.paygo.constants.AppConstants;
import com.cam.paygo.model.request.HeartbeatRequest;
import com.cam.paygo.model.response.HeartbeatResponse;
import com.cam.paygo.repository.HeartbeatRepository;
import com.cam.paygo.utils.AppLogger;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HeartbeatManager {

    private static final String TAG = "Heart Beat Manager";
    @SuppressLint("StaticFieldLeak")
    private static HeartbeatManager instance;
    private final Handler handler;
    private Runnable runnable;
    private int interval = 30_000;
    private boolean isRunning = false;
    private final HeartbeatRepository repository;

    private HeartbeatManager() {
        handler = new Handler(Looper.getMainLooper());
        repository = new HeartbeatRepository();
    }

    public static synchronized HeartbeatManager getInstance() {
        if (instance == null) {
            instance = new HeartbeatManager();
        }
        return instance;
    }

    // 🔥 Start Heartbeat Loop
    public void start(String token, Context ctx) {
        if (isRunning && runnable != null) {
            handler.removeCallbacks(runnable);
        }

        isRunning = true;
        interval = 30_000;

        runnable = new Runnable() {
            @Override
            public void run() {

                callHeartbeat(token, ctx);

                handler.postDelayed(this, interval);
            }
        };

        handler.post(runnable);
    }

    // 🔥 Stop Heartbeat
    public void stop() {
        isRunning = false;

        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    // 🔥 API Call
    private void callHeartbeat(String token, Context context) {

        HeartbeatRequest request = new HeartbeatRequest(getDeviceSerialNumber());

        AppLogger.api_log(context, AppConstants.HEARTBEAT, "Heart Beat API Called");

        repository.sendHeartbeat(token, request, new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<HeartbeatResponse> call, @NonNull Response<HeartbeatResponse> response) {
                HeartbeatResponse body = response.body();
                if (body != null) {
                    AppLogger.api_log(context, "HEARTBEAT API RESPONSE", body.getMessage());
                } else {
                    AppLogger.api_log(context, "HEARTBEAT API RESPONSE", "Empty body");
                }

                if (response.isSuccessful() && body != null && body.getData() != null) {

                    int intervalSec = body.getData().getHeartbeatIntervalSec();

                    interval = intervalSec * 1000;

                    Log.d(AppConstants.HEARTBEAT, "Next interval: " + interval);

                } else {
                    handleFailure();
                }
            }

            @Override
            public void onFailure(@NonNull Call<HeartbeatResponse> call,
                                  @NonNull Throwable t) {
                Log.e(AppConstants.HEARTBEAT_ERROR, String.valueOf(t.getMessage()), t);

                if (t instanceof IOException) {
                    // Internet issue
                    AppLogger.api_log(context, AppConstants.HEARTBEAT_ERROR, "No Internet / Network Failure");
                } else {
                    // Other issue
                    AppLogger.api_log(context, AppConstants.HEARTBEAT_ERROR, "Unknown Error : " + t.getMessage());
                }

                handleFailure();
            }
        });
    }

    private void handleFailure() {
        if (interval <= 0) {
            interval = 10000; // default 10 sec
        } else {
            interval = Math.min(interval * 2, 300000);
        }

        Log.d(AppConstants.HEARTBEAT, "Retry heartbeat in: " + interval + " ms");
    }
}
