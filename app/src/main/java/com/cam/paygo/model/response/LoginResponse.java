package com.cam.paygo.model.response;

import com.google.gson.annotations.SerializedName;

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

    // 🔽 INNER CLASS
    public static class Data {

        @SerializedName("token")
        private String token;

        @SerializedName("user")
        private User user;

        public String getToken() {
            return token;
        }

        public User getUser() {
            return user;
        }
    }

    // 🔽 USER CLASS
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

