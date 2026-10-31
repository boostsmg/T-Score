package co.tinode.tindroid;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class AppUpdater {
    private static final String MANIFEST_URL = "https://chat.socialrise.garden/downloads/version.json";
    private static final String FALLBACK_APK_URL = "https://chat.socialrise.garden/downloads/T-Score.apk";

    private AppUpdater() {}

    public static void checkAndUpdate(Activity activity) {
        Toast.makeText(activity, "Checking for updates...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();
                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Update server returned " + conn.getResponseCode());
                }

                StringBuilder body = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) body.append(line);
                } finally {
                    conn.disconnect();
                }

                JSONObject json = new JSONObject(body.toString());
                int latestCode = json.getInt("version_code");
                String latestName = json.optString("version_name", "new version");
                String apkUrl = json.optString("download_url", FALLBACK_APK_URL);

                activity.runOnUiThread(() -> {
                    if (latestCode <= BuildConfig.VERSION_CODE) {
                        Toast.makeText(activity, "T-Score is already up to date", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity, "New T-Score " + latestName + " available", Toast.LENGTH_SHORT).show();
                        downloadAndInstall(activity, apkUrl);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Could not check for updates. Please try again.", Toast.LENGTH_LONG).show());
            }
        }, "tscore-update-check").start();
    }

    // Backward-compatible entry point for existing button wiring.
    public static void downloadAndInstall(Activity activity) {
        checkAndUpdate(activity);
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            Toast.makeText(activity, "Allow T-Score to install updates, then tap Update App again.", Toast.LENGTH_LONG).show();
            return;
        }

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            Toast.makeText(activity, "Download service unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("T-Score update")
                .setDescription("Downloading latest T-Score version")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, "T-Score-update.apk");

        long downloadId = dm.enqueue(req);
        Toast.makeText(activity, "Update download started", Toast.LENGTH_SHORT).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;

                Uri apkUri = dm.getUriForDownloadedFile(downloadId);
                if (apkUri == null) {
                    Toast.makeText(activity, "Update download failed", Toast.LENGTH_LONG).show();
                    try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
                    return;
                }

                Intent install = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apkUri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                activity.startActivity(install);
                try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
    }
}
