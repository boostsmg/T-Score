package co.tinode.tindroid;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only client for the public canonical T-Score trust API. */
public final class TrustApiClient {
    private static final String BASE_URL = "https://chat.socialrise.garden/tscore-api/v1/trust/users/";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(Result result);
        void onError(Exception error);
    }

    public static final class Result {
        public final String userId;
        public final int score;
        public final String badge;
        public final String standing;
        public final Map<String, Boolean> verified;
        public final Map<String, Integer> breakdown;

        Result(String userId, int score, String badge, String standing,
               Map<String, Boolean> verified, Map<String, Integer> breakdown) {
            this.userId = userId;
            this.score = score;
            this.badge = badge;
            this.standing = standing;
            this.verified = Collections.unmodifiableMap(verified);
            this.breakdown = Collections.unmodifiableMap(breakdown);
        }
    }

    private TrustApiClient() { }

    public static void fetch(String userId, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String clean = userId == null ? "" : userId.trim();
                if (!clean.startsWith("usr")) throw new IllegalArgumentException("Invalid T-Score user ID");
                String encoded = URLEncoder.encode(clean, StandardCharsets.UTF_8.name()).replace("+", "%20");
                URL url = new URL(BASE_URL + encoded);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setUseCaches(false);

                int code = conn.getResponseCode();
                InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
                String body = readAll(stream);
                if (code < 200 || code >= 300) throw new IllegalStateException("Trust API HTTP " + code);

                JSONObject root = new JSONObject(body);
                JSONObject trust = root.getJSONObject("trust");
                JSONObject verifiedJson = root.optJSONObject("verified");
                JSONObject breakdownJson = trust.optJSONObject("breakdown");

                Map<String, Boolean> verified = new HashMap<>();
                String[] verifyKeys = {"profile_photo", "email", "phone", "identity", "address", "liveness"};
                if (verifiedJson != null) for (String key : verifyKeys) verified.put(key, verifiedJson.optBoolean(key, false));

                Map<String, Integer> breakdown = new HashMap<>();
                String[] scoreKeys = {"profile_photo", "email", "phone", "identity", "address", "liveness",
                        "account_age", "successful_deals", "reviews", "account_standing"};
                if (breakdownJson != null) for (String key : scoreKeys) breakdown.put(key, breakdownJson.optInt(key, 0));

                Result result = new Result(
                        root.optString("user_id", clean),
                        trust.optInt("score", 0),
                        trust.optString("badge", "New"),
                        root.optString("standing", "GOOD"),
                        verified,
                        breakdown);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e));
            } finally {
                if (conn != null) conn.disconnect();
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
