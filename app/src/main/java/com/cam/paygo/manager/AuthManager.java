package com.cam.paygo.manager;

import android.content.Context;
import android.content.SharedPreferences;

import com.cam.paygo.constants.AppConstants;

public class AuthManager {

    private static String token;

    public static void setToken(Context context, String t) {
        token = t;

        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
        prefs.edit().putString(AppConstants.KEY_AUTH_TOKEN, t).apply();
    }

    public static String getToken(Context context) {
        if (token != null) {
            return token;
        }

        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
        token = prefs.getString(AppConstants.KEY_AUTH_TOKEN, null);

        return token;
    }

    public static void clearToken(Context context) {
        token = null;

        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREF_AUTH, Context.MODE_PRIVATE);
        prefs.edit().remove(AppConstants.KEY_AUTH_TOKEN).apply();
    }
}