package com.cam.paygo.utils;

import android.os.Build;
import android.util.Log;

import com.cam.paygo.constants.BankConstants;

/**
 * Detects PAX terminals that ship Neptune / libpaxapijni.so.
 * Non-PAX devices must not open UART or they crash with UnsatisfiedLinkError.
 */
public final class PaxDeviceHelper {

    private static final String TAG = "PaxDeviceHelper";

    private static Boolean cached;

    private PaxDeviceHelper() {
    }

    public static boolean isPaxDevice() {
        if (cached != null) {
            return cached;
        }
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        boolean match = BankConstants.DEVICE_MODEL_IM30.equalsIgnoreCase(model)
                || BankConstants.DEVICE_MODEL_A910S.equalsIgnoreCase(model)
                || "PAX".equalsIgnoreCase(manufacturer)
                || model.toUpperCase().startsWith("PAX");
        cached = match;
        Log.i(TAG, "isPaxDevice=" + match + " model=" + model + " manufacturer=" + manufacturer);
        return match;
    }
}
