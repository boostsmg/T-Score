package co.tinode.tindroid;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ReputationApiClient {
    private static final String BASE = "https://chat.socialrise.garden/tscore-api/v1/reputation";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class ReviewRecord {
        final String reviewerId;
        final String revieweeId;
        final int rating;
        final String comment;
        ReviewRecord(JSONObject o) {
            reviewerId = o.optString("reviewer_id", "");
            revieweeId = o.optString("reviewee_id", "");
            rating = o.optInt("rating", 0);
            comment = o.optString("comment", "");
        }
    }

    static final class Summary {
        final String userId;
        final int reviewCount;
        final double averageRating;
        final int completedDeals;
        final int disputedDeals;
        Summary(JSONObject o) {
            userId = o.optString("user_id", "");
            reviewCount = o.optInt("review_count", 0);
            averageRating = o.optDouble("average_rating", 0.0);
            completedDeals = o.optInt("completed_deals", 0);
            disputedDeals = o.optInt("disputed_deals", 0);
        }
    }

    interface Callback { void onSuccess(); void onFailure(String message); }
    interface DealCallback { void onSuccess(DealApiClient.RemoteDeal deal); void onFailure(String message); }
    interface ReviewsCallback { void onSuccess(List<ReviewRecord> reviews); void onFailure(String message); }
    interface SummaryCallback { void onSuccess(Summary summary); void onFailure(String message); }

    private ReputationApiClient() {}

    static void fetchSummary(String userId, SummaryCallback callback) {
        if (userId == null || !userId.startsWith("usr")) {
            callback.onFailure("Reputation unavailable");
            return;
        }
        EXECUTOR.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = open(BASE + "/users/" + userId + "/summary", "GET", null);
                int code = c.getResponseCode();
                String response = readResponse(c, code);
                if (code >= 200 && code < 300) {
                    Summary summary = new Summary(new JSONObject(response));
                    MAIN.post(() -> callback.onSuccess(summary));
                } else {
                    String msg = apiError(response, "Reputation unavailable");
                    MAIN.post(() -> callback.onFailure(msg));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Reputation server unavailable"));
            } finally { if (c != null) c.disconnect(); }
        });
    }

    static void submitReview(String dealId, int rating, String comment, Callback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("deal_id", dealId);
            body.put("rating", rating);
            body.put("comment", comment == null ? "" : comment.trim());
        } catch (Exception e) { callback.onFailure("Invalid review"); return; }
        post(BASE + "/reviews", body, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) { MAIN.post(callback::onSuccess); }
            @Override public void onFailure(String message) { MAIN.post(() -> callback.onFailure(message)); }
        });
    }

    static void openDispute(String dealId, String reason, String details, DealCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("deal_id", dealId);
            body.put("reason", reason);
            body.put("details", details == null ? "" : details.trim());
        } catch (Exception e) { callback.onFailure("Invalid dispute"); return; }
        post(BASE + "/disputes", body, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) {
                JSONObject d = json.optJSONObject("deal");
                if (d == null) { MAIN.post(() -> callback.onFailure("Invalid server response")); return; }
                DealApiClient.RemoteDeal deal = new DealApiClient.RemoteDeal(d);
                MAIN.post(() -> callback.onSuccess(deal));
            }
            @Override public void onFailure(String message) { MAIN.post(() -> callback.onFailure(message)); }
        });
    }

    static void listReviews(String dealId, ReviewsCallback callback) {
        String token = token();
        if (token == null) { callback.onFailure("Login session unavailable"); return; }
        EXECUTOR.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = open(BASE + "/deals/" + dealId + "/reviews", "GET", token);
                int code = c.getResponseCode();
                String response = readResponse(c, code);
                if (code >= 200 && code < 300) {
                    JSONArray arr = new JSONObject(response).optJSONArray("reviews");
                    List<ReviewRecord> out = new ArrayList<>();
                    if (arr != null) for (int i=0;i<arr.length();i++) { JSONObject o=arr.optJSONObject(i); if(o!=null) out.add(new ReviewRecord(o)); }
                    List<ReviewRecord> finalOut = out;
                    MAIN.post(() -> callback.onSuccess(finalOut));
                } else {
                    String msg = apiError(response, "Reviews unavailable");
                    MAIN.post(() -> callback.onFailure(msg));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure("Reputation server unavailable"));
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private interface JsonCallback { void onSuccess(JSONObject json); void onFailure(String message); }

    private static void post(String url, JSONObject body, JsonCallback callback) {
        String token = token();
        if (token == null) { callback.onFailure("Login session unavailable"); return; }
        EXECUTOR.execute(() -> {
            HttpURLConnection c = null;
            try {
                c = open(url, "POST", token);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(payload.length);
                try (OutputStream out = c.getOutputStream()) { out.write(payload); }
                int code = c.getResponseCode();
                String response = readResponse(c, code);
                if (code >= 200 && code < 300) callback.onSuccess(new JSONObject(response));
                else callback.onFailure(apiError(response, "Request failed"));
            } catch (Exception e) {
                callback.onFailure("Reputation server unavailable");
            } finally { if (c != null) c.disconnect(); }
        });
    }

    private static String token() {
        String t = Cache.getTinode().getAuthToken();
        return t == null || t.trim().isEmpty() ? null : t;
    }

    private static HttpURLConnection open(String url, String method, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/json");
        if ("POST".equals(method)) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8"); }
        return c;
    }

    private static String readResponse(HttpURLConnection c, int code) throws Exception {
        return readAll(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream());
    }
    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder s = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) { String line; while ((line=r.readLine())!=null) s.append(line); }
        return s.toString();
    }
    private static String apiError(String response, String fallback) {
        try { String e = new JSONObject(response).optString("error", ""); return e.isEmpty() ? fallback : e; }
        catch (Exception ignored) { return fallback; }
    }
}
