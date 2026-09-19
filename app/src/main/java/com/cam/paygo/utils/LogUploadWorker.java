package com.cam.paygo.utils;

import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cam.paygo.api.ApiClient;
import com.cam.paygo.api.ApiConstants;
import com.cam.paygo.api.TokenRefresher;
import com.cam.paygo.manager.AuthManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LogUploadWorker extends Worker {

    private static final String TAG = "LogsUploadWorker";

    private static final String TRXN_LOG_DIR = "trxn_logs";
    private static final String API_LOG_DIR = "api_logs";

    public LogUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        Log.d(TAG, "Logs Upload Worker Called");
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Do Work called");

        try {
            File baseDir = getApplicationContext().getFilesDir();

            if (baseDir == null) {
                Log.e(TAG, "Base directory is NULL");
                return Result.retry();
            }

            File txnDir = new File(baseDir, TRXN_LOG_DIR);
            File apiDir = new File(baseDir, API_LOG_DIR);

            boolean allUploaded = true;

            // 🔹 Process API logs
            if (apiDir.exists()) {
                File[] apiFiles = apiDir.listFiles();

                if (apiFiles != null && apiFiles.length > 0) {
                    for (File file : apiFiles) {

                        Log.d(TAG, "Uploading API file: " + file.getName());

                        boolean uploaded = uploadFile(file, API_LOG_DIR);

                        if (uploaded) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "API Uploaded & deleted: " + deleted);
                        } else {
                            Log.e(TAG, "API Upload failed");
                            allUploaded = false;
                        }
                    }
                } else {
                    Log.d(TAG, "No API log files found");
                }
            } else {
                Log.d(TAG, "API directory does not exist");
            }

            // 🔹 Process TRXN logs
            if (txnDir.exists()) {
                File[] trxnFiles = txnDir.listFiles();

                if (trxnFiles != null && trxnFiles.length > 0) {
                    for (File file : trxnFiles) {

                        Log.d(TAG, "Uploading TRXN file: " + file.getName());

                        boolean uploaded = uploadFile(file, TRXN_LOG_DIR);

                        if (uploaded) {
                            boolean deleted = file.delete();
                            Log.d(TAG, "TRXN Uploaded & deleted: " + deleted);
                        } else {
                            Log.e(TAG, "TRXN Upload failed");
                            allUploaded = false;
                        }
                    }
                } else {
                    Log.d(TAG, "No TRXN log files found");
                }
            } else {
                Log.d(TAG, "TRXN directory does not exist");
            }

            // 🔹 Final result
            if (allUploaded) {
                return Result.success();
            } else {
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Worker failed", e);
            return Result.retry();
        }
    }

    private boolean uploadFile(File file, String typeOfLogs) {

        Response response = null;
        File tempUploadFile = null;

        try {
            Log.d(TAG, "Uploading file: " + file.getName());

            AuthManager.init(getApplicationContext());

            // ✅ Create temp upload directory
            File tempDir = new File(getApplicationContext().getCacheDir(), "upload_temp");

            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            // ✅ Create stable temp copy
            tempUploadFile = new File(tempDir, System.currentTimeMillis() + "_" + file.getName());

            copyFile(file, tempUploadFile);

            Log.d(TAG, "Temp upload file created: " + tempUploadFile.getAbsolutePath());

            OkHttpClient client = ApiClient.getInstance().getAuthedOkHttpClient();

            String token = AuthManager.getToken(getApplicationContext());
            if (token == null || token.isEmpty()) {
                Log.e(TAG, "No auth token for log upload");
                return false;
            }

            String serialNumber = getDeviceSerialNumber();

            // ✅ Upload TEMP FILE instead of original file
            RequestBody fileBody =
                    RequestBody.create(
                            tempUploadFile,
                            MediaType.parse("text/plain")
                    );

            MultipartBody requestBody =
                    new MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("serial_number", serialNumber)
                            .addFormDataPart(
                                    "log_category",
                                    Objects.equals(
                                            typeOfLogs,
                                            "trxn_logs"
                                    ) ? "TRXN" : "API"
                            )
                            .addFormDataPart(
                                    "log_type",
                                    "INFO"
                            )
                            .addFormDataPart(
                                    "log_file",
                                    file.getName(),
                                    fileBody
                            )
                            .build();

            Request request = new Request.Builder()
                    .url(ApiConstants.BASE_URL + ApiConstants.PATH_FILE_UPLOAD)
                    .post(requestBody)
                    .header(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_PREFIX + token)
                    .build();

            response = client.newCall(request).execute();

            // Authed client already retries once on 401 via TokenAuthenticator.
            // If still unauthorized, attempt one explicit refresh + retry for this raw call path.
            if (response.code() == 401) {
                response.close();
                String refreshed = TokenRefresher.refreshBlocking();
                if (refreshed == null || refreshed.isEmpty()) {
                    Log.e(TAG, "Log upload unauthorized; refresh failed");
                    return false;
                }
                Request retry = request.newBuilder()
                        .header(ApiConstants.HEADER_AUTHORIZATION, ApiConstants.BEARER_PREFIX + refreshed)
                        .build();
                response = client.newCall(retry).execute();
            }

            String responseBody = response.body() != null ? response.body().string() : "";

            Log.d(TAG, "Response Code: " + response.code());
            Log.d(TAG, "Response Body: " + responseBody);

            boolean success = response.isSuccessful();

            // ✅ Delete original file after successful upload
            if (success) {
                boolean deleted = file.delete();
                Log.d(TAG, "Uploaded file deleted: " + deleted);
            }

            return success;

        } catch (Exception e) {
            Log.e(TAG, "Upload error", e);
            return false;
        } finally {

            // ✅ Close response
            if (response != null) {
                response.close();
            }

            // ✅ Delete temp upload file ALWAYS
            if (tempUploadFile != null && tempUploadFile.exists()) {
                boolean deleted = tempUploadFile.delete();
                Log.d(TAG, "Temp upload file deleted: " + deleted);
            }
        }
    }

    private void copyFile(File source, File destination)
            throws IOException {

        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {

            byte[] buffer = new byte[8192];

            int length;

            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            out.flush();
        }
    }
}