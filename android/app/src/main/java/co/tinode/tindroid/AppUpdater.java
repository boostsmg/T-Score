package co.tinode.tindroid;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
                String manifestUrl = addCacheBuster(MANIFEST_URL, BuildConfig.VERSION_CODE, System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) new URL(manifestUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                conn.setRequestProperty("Pragma", "no-cache");
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
                        Toast.makeText(activity,
                                "T-Score " + BuildConfig.VERSION_NAME + " is already up to date",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(activity,
                                "New T-Score " + latestName + " available",
                                Toast.LENGTH_SHORT).show();
                        downloadAndInstall(activity, apkUrl, latestCode, latestName);
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

    private static void downloadAndInstall(Activity activity, String apkUrl,
                                           int expectedVersionCode, String expectedVersionName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            Toast.makeText(activity,
                    "Allow T-Score to install updates, then tap Update App again.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            Toast.makeText(activity, "Download service unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        String freshUrl = addCacheBuster(apkUrl, expectedVersionCode, System.currentTimeMillis());
        String fileName = "T-Score-update-" + expectedVersionCode + ".apk";

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(freshUrl))
                .setTitle("T-Score " + expectedVersionName)
                .setDescription("Downloading verified T-Score update")
                .setMimeType("application/vnd.android.package-archive")
                .addRequestHeader("Cache-Control", "no-cache, no-store, max-age=0")
                .addRequestHeader("Pragma", "no-cache")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);

        long downloadId = dm.enqueue(req);
        Toast.makeText(activity,
                "Downloading T-Score " + expectedVersionName,
                Toast.LENGTH_SHORT).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != downloadId) return;

                try {
                    int status = getDownloadStatus(dm, downloadId);
                    if (status != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(activity, "Update download failed", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Uri apkUri = dm.getUriForDownloadedFile(downloadId);
                    if (apkUri == null) {
                        Toast.makeText(activity, "Update file unavailable", Toast.LENGTH_LONG).show();
                        return;
                    }

                    PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(
                            apkUri.getPath() == null ? "" : apkUri.getPath(), 0);
                    // DownloadManager commonly exposes a content:// URI, so getPackageArchiveInfo may
                    // not be able to inspect it directly. The installer is still allowed in that case.
                    if (info != null) {
                        long downloadedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? info.getLongVersionCode() : info.versionCode;
                        if (!activity.getPackageName().equals(info.packageName) ||
                                downloadedCode < expectedVersionCode) {
                            Toast.makeText(activity,
                                    "Downloaded update is stale or invalid. Please try again.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                    }

                    Intent install = new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(apkUri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(install);
                } finally {
                    try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(receiver, filter);
        }
    }

    private static int getDownloadStatus(DownloadManager dm, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = dm.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                return index >= 0 ? cursor.getInt(index) : -1;
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String addCacheBuster(String url, int versionCode, long timestamp) {
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "v=" + versionCode + "&ts=" + timestamp;
    }
}
