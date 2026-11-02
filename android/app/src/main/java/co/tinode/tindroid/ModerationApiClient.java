package co.tinode.tindroid;

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

final class ModerationApiClient {
    private static final String URL = "https://chat.socialrise.garden/tscore-api/v1/reports";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onSuccess(String reportId);
        void onFailure(String message);
    }

    private ModerationApiClient() {}

    static void submit(String targetId, String topicName, String reason, String details, Callback callback) {
        if (targetId == null || targetId.trim().isEmpty()) {
            callback.onFailure("Could not identify the reported account.");
            return;
        }
        String token = Cache.getTinode().getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            callback.onFailure("Login session unavailable. Please reconnect and try again.");
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection c = null;
            try {
                JSONObject body = new JSONObject();
                body.put("target_id", targetId.trim());
                body.put("topic_name", topicName == null ? "" : topicName.trim());
                body.put("reason", reason == null ? "OTHER" : reason.trim());
                body.put("details", details == null ? "" : details.trim());

                c = (HttpURLConnection) new URL(URL).openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                c.setDoOutput(true);
                c.setRequestProperty("Authorization", "Bearer " + token);
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = c.getOutputStream()) { out.write(payload); }

                int code = c.getResponseCode();
                String response = readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
                if (code >= 200 && code < 300) {
                    String id = new JSONObject(response).optString("id", "");
                    MAIN.post(() -> callback.onSuccess(id));
                } else {
                    String message = apiError(response, "Could not submit report");
                    MAIN.post(() -> callback.onFailure(message));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Moderation server unavailable. The contact can still be blocked."));
            } finally {
                if (c != null) c.disconnect();
            }
        });
    }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String apiError(String response, String fallback) {
        try {
            String error = new JSONObject(response).optString("error", "");
            return error.isEmpty() ? fallback : error;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
