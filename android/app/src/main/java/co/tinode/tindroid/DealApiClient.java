package co.tinode.tindroid;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DealApiClient {
    private static final String BASE_URL = "https://chat.socialrise.garden/tscore-api/v1/deals";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class RemoteDeal {
        final String id;
        final String buyerId;
        final String sellerId;
        final String createdBy;
        final String title;
        final String terms;
        final long amountMinor;
        final String currency;
        final String status;

        RemoteDeal(JSONObject json) {
            id = json.optString("id", "");
            buyerId = json.optString("buyer_id", "");
            sellerId = json.optString("seller_id", "");
            createdBy = json.optString("created_by", "");
            title = json.optString("title", "Deal");
            terms = json.optString("terms", "");
            amountMinor = json.optLong("amount_minor", 0);
            currency = json.optString("currency", "");
            status = json.optString("status", "PROPOSED");
        }

        String counterpartFor(String me) {
            return me != null && me.equals(buyerId) ? sellerId : buyerId;
        }

        String amountDisplay() {
            return BigDecimal.valueOf(amountMinor, 2).stripTrailingZeros().toPlainString();
        }
    }

    interface Callback {
        void onSuccess(String dealId);
        void onFailure(String message);
    }

    interface ListCallback {
        void onSuccess(List<RemoteDeal> deals);
        void onFailure(String message);
    }

    interface DealCallback {
        void onSuccess(RemoteDeal deal);
        void onFailure(String message);
    }

    private DealApiClient() {}

    static void create(DealDraft draft, Callback callback) {
        String token = authToken();
        if (token == null) {
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

                connection = open(BASE_URL, "POST", token);
                writeBody(connection, body);
                int code = connection.getResponseCode();
                String response = readResponse(connection, code);
                if (code >= 200 && code < 300) {
                    JSONObject json = new JSONObject(response);
                    String id = json.optString("id", "");
                    MAIN.post(() -> callback.onSuccess(id));
                } else {
                    postFailure(callback, apiError(response, "Could not send deal"));
                }
            } catch (Exception e) {
                postFailure(callback, "Deal server is not available yet. Draft kept safely on this device.");
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void list(ListCallback callback) {
        String token = authToken();
        if (token == null) {
            callback.onFailure("Login session unavailable");
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = open(BASE_URL + "?limit=100", "GET", token);
                int code = connection.getResponseCode();
                String response = readResponse(connection, code);
                if (code >= 200 && code < 300) {
                    JSONObject root = new JSONObject(response);
                    JSONArray array = root.optJSONArray("deals");
                    List<RemoteDeal> out = new ArrayList<>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.optJSONObject(i);
                            if (item != null) out.add(new RemoteDeal(item));
                        }
                    }
                    MAIN.post(() -> callback.onSuccess(out));
                } else {
                    MAIN.post(() -> callback.onFailure(apiError(response, "Deals unavailable")));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Deals server unavailable"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    static void transition(String dealId, String status, DealCallback callback) {
        String token = authToken();
        if (token == null) {
            callback.onFailure("Login session unavailable");
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject();
                body.put("status", status);
                connection = open(BASE_URL + "/" + dealId + "/transition", "POST", token);
                writeBody(connection, body);
                int code = connection.getResponseCode();
                String response = readResponse(connection, code);
                if (code >= 200 && code < 300) {
                    RemoteDeal deal = new RemoteDeal(new JSONObject(response));
                    MAIN.post(() -> callback.onSuccess(deal));
                } else {
                    MAIN.post(() -> callback.onFailure(apiError(response, "Deal action failed")));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Deals server unavailable"));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String authToken() {
        String token = Cache.getTinode().getAuthToken();
        return token == null || token.trim().isEmpty() ? null : token;
    }

    private static HttpURLConnection open(String url, String method, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private static void writeBody(HttpURLConnection connection, JSONObject body) throws Exception {
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(payload.length);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }
    }

    private static String readResponse(HttpURLConnection connection, int code) throws Exception {
        return readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
    }

    private static String apiError(String response, String fallback) {
        try {
            String error = new JSONObject(response).optString("error", "");
            return error.isEmpty() ? fallback : error;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void postFailure(Callback callback, String message) {
        MAIN.post(() -> callback.onFailure(message));
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