package com.cam.paygo.utils;


import static com.cam.paygo.MainActivity.getDeviceSerialNumber;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLogger {
    private static final String TRXN_LOG_DIR = "trxn_logs";
    private static final String API_LOG_DIR = "api_logs";
    private static final String TAG = "AppLogger";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void trxn_log(Context context, String tag, String message) {
        executor.execute(() -> writeToFile(context, tag, message));
    }

    public static void api_log(Context context, String tag, String message) {
        executor.execute(() -> writeApiLogsToFile(context, tag, message));
    }

    private static void writeToFile(Context context, String tag, String message) {

        if (context == null) {
            Log.e("TrxnLogger", "Context is NULL. Skipping log write.");
            return;
        }

        FileWriter writer = null;

        try {
            // 🔹 Safe serial number
            String serialNumber = getDeviceSerialNumber();
            if (serialNumber == null || serialNumber.isEmpty()) {
                serialNumber = "UNKNOWN_SN";
            }

            // 🔹 Base directory
            File baseDir = context.getFilesDir();
            if (baseDir == null) {
                Log.e("TrxnLogger", "getFilesDir() returned NULL");
                return;
            }

            File dir = new File(baseDir, TRXN_LOG_DIR);

            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                Log.d(TAG, "Log dir created: " + created);
            }

            Log.d("LOGGER_PATH", baseDir.getAbsolutePath());

            // 🔹 File name (daily rotation)
            String fileName = "trxn_logs" + serialNumber + "_" +
                    new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) +
                    ".txt";

            File logFile = new File(dir, fileName);

            Log.d(TAG, "Writing to file: " + logFile.getAbsolutePath());

            // 🔹 Log content
            String logLine = String.format(
                    "%s | SN:%s | %s | %s\n",
                    getCurrentTimestamp(),
                    serialNumber,
                    tag,
                    message
            );

            // 🔹 Write safely
            writer = new FileWriter(logFile, true);
            writer.append(logLine);
            writer.flush();

        } catch (Exception e) {
            Log.e("LOGGER_ERROR", "File write failed", e);

        } finally {
            // 🔹 Always close writer
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    Log.e("LOGGER_ERROR", "Failed to close writer", e);
                }
            }
        }
    }

    private static void writeApiLogsToFile(Context context, String tag, String message) {

        if (context == null) {
            Log.e("AppLogger", "Context is NULL. Skipping log write.");
            return;
        }

        FileWriter writer = null;

        try {
            // 🔹 Safe serial number
            String serialNumber = getDeviceSerialNumber();
            if (serialNumber == null || serialNumber.isEmpty()) {
                serialNumber = "UNKNOWN_SN";
            }

            // 🔹 Get base directory once
            File baseDir = context.getFilesDir();
            if (baseDir == null) {
                Log.e("AppLogger", "getFilesDir() returned NULL");
                return;
            }

            File dir = new File(baseDir, API_LOG_DIR);

            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                Log.d(TAG, "Log dir created: " + created);
            }

            Log.d("LOGGER_PATH", baseDir.getAbsolutePath());

            // 🔹 File name
            String fileName = "api_logs_" + serialNumber + "_" +
                    new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date()) +
                    ".txt";

            File logFile = new File(dir, fileName);

            Log.d(TAG, "Writing to file: " + logFile.getAbsolutePath());

            // 🔹 Log content
            String logLine = String.format(
                    "%s | SN:%s | %s | %s\n",
                    getCurrentTimestamp(),
                    serialNumber,
                    tag,
                    message
            );

            // 🔹 Write file
            writer = new FileWriter(logFile, true);
            writer.append(logLine);
            writer.flush();

        } catch (Exception e) {
            Log.e("LOGGER_ERROR", "File write failed", e);

        } finally {
            // 🔹 Always close safely
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    Log.e("LOGGER_ERROR", "Failed to close writer", e);
                }
            }
        }
    }

    public static String getCurrentTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return LocalDateTime.now().format(formatter);
    }
}
