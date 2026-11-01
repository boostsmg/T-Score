package co.tinode.tindroid;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DealApiClient {
    private static final String CREATE_URL = "https://chat.socialrise.garden/tscore-api/v1/deals";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onSuccess(String dealId);
        void onFailure(String message);
    }

    private DealApiClient() {}

    static void create(DealDraft draft, Callback callback) {
        String token = Cache.getTinode().getAuthToken();
        if (token == null || token.trim().isEmpty()) {
            callback.onFailure("Login session unavailable. Please reconnect and try again.");
            return;
        }
        if (draft == null || draft.counterpart == null || !draft.counterpart.startsWith("usr")) {
            callback.onFailure("Select a T-Score directory user before sending this deal.");
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                long amountMinor = new BigDecimal(draft.amount).movePointRight(2).longValueExact();
                JSONObject body = new JSONObject();
                body.put("counterpart_id", draft.counterpart);
                body.put("role", draft.role);
                body.put("title", draft.title);
                body.put("terms", draft.terms);
                body.put("amount_minor", amountMinor);
                body.put("currency", draft.currency);

                connection = (HttpURLConnection) new URL(CREATE_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + token);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }

                int code = connection.getResponseCode();
                String response = readAll(code >= 200 && code < 300
                        ? connection.getInputStream() : connection.getErrorStream());
                if (code >= 200 && code < 300) {
                    JSONObject json = new JSONObject(response);
                    String id = json.optString("id", "");
                    MAIN.post(() -> callback.onSuccess(id));
                } else {
                    String message = "Could not send deal";
                    try {
                        String apiError = new JSONObject(response).optString("error", "");
                        if (!apiError.isEmpty()) message = apiError;
                    } catch (Exception ignored) {}
                    String finalMessage = message;
                    MAIN.post(() -> callback.onFailure(finalMessage));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Deal server is not available yet. Draft kept safely on this device."));
            } finally {
                if (connection != null) connection.disconnect();
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
}