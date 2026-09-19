package com.cam.paygo.model.response;

import com.cam.paygo.constants.AppConstants;
import com.google.gson.annotations.SerializedName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LoginResponse {

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

        @SerializedName("token")
        private String token;

        /** F8Ops-style lifetime in seconds (optional). */
        @SerializedName("expires_in")
        private long expiresIn;

        /** f8tms lifetime in minutes. */
        @SerializedName("token_validity_minutes")
        private int tokenValidityMinutes;

        /** f8tms absolute expiry (ISO-8601). */
        @SerializedName("token_expires_at")
        private String tokenExpiresAt;

        @SerializedName("user")
        private User user;

        public String getToken() {
            return token;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public int getTokenValidityMinutes() {
            return tokenValidityMinutes;
        }

        public String getTokenExpiresAt() {
            return tokenExpiresAt;
        }

        public User getUser() {
            return user;
        }

        /**
         * Resolve access-token TTL in seconds for either f8tms or F8Ops response shapes.
         */
        public long resolveExpiresInSeconds() {
            if (expiresIn > 0L) {
                return expiresIn;
            }
            if (tokenValidityMinutes > 0) {
                return tokenValidityMinutes * 60L;
            }
            long fromAbsolute = secondsUntil(tokenExpiresAt);
            if (fromAbsolute > 0L) {
                return fromAbsolute;
            }
            return AppConstants.DEFAULT_TOKEN_EXPIRES_IN_SEC;
        }

        private static long secondsUntil(String iso) {
            if (iso == null || iso.trim().isEmpty()) {
                return 0L;
            }
            String value = iso.trim();
            // Go time.Time JSON is usually 2006-01-02T15:04:05Z07:00
            String[] patterns = {
                    "yyyy-MM-dd'T'HH:mm:ssX",
                    "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                    "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            };
            for (String pattern : patterns) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date date = sdf.parse(value);
                    if (date == null) {
                        continue;
                    }
                    long diffMs = date.getTime() - System.currentTimeMillis();
                    return Math.max(0L, diffMs / 1000L);
                } catch (ParseException ignored) {
                    // try next
                }
            }
            return 0L;
        }
    }

    public static class User {

        @SerializedName("id")
        private int id;

        @SerializedName("name")
        private String name;

        @SerializedName("email")
        private String email;

        @SerializedName("role")
        private String role;

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }
}
