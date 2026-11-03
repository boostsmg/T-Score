package com.tscore.clean;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    interface Callback {
        void onSuccess(JSONObject body);
        void onError(String message);
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    void login(String login, String password, Callback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("login", login);
            payload.put("password", password);
            post("/api/v1/auth/login", payload, null, callback);
        } catch (Exception e) {
            callback.onError("Unable to prepare login request");
        }
    }

    void signup(String username, String email, String fullName, String password, Callback callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("username", username);
            payload.put("email", email);
            payload.put("full_name", fullName);
            payload.put("password", password);
            post("/api/v1/auth/signup", payload, null, callback);
        } catch (Exception e) {
            callback.onError("Unable to prepare signup request");
        }
    }

    void me(String token, Callback callback) {
        request("GET", "/api/v1/me", null, token, callback);
    }

    void logout(String token, Callback callback) {
        post("/api/v1/auth/logout", new JSONObject(), token, callback);
    }

    private void post(String path, JSONObject payload, String token, Callback callback) {
        request("POST", path, payload, token, callback);
    }

    private void request(String method, String path, JSONObject payload, String token, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(baseUrl + path);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestMethod(method);
                conn.setRequestProperty("Accept", "application/json");
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                if (payload != null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream out = conn.getOutputStream()) {
                        out.write(bytes);
                    }
                }

                int code = conn.getResponseCode();
                InputStream input = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(input);
                JSONObject json = body.isEmpty() ? new JSONObject() : new JSONObject(body);

                if (code >= 200 && code < 300) {
                    main.post(() -> callback.onSuccess(json));
                } else {
                    String detail = json.optString("detail", "Request failed");
                    main.post(() -> callback.onError(detail));
                }
            } catch (Exception e) {
                main.post(() -> callback.onError("Server connection failed"));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
