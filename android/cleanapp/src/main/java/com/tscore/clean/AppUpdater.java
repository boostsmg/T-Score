package com.tscore.clean;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppUpdater {
    private static final String VERSION_URL = "https://chat.socialrise.garden/downloads/version.json";
    private static final String EXPECTED_PACKAGE = "com.tscore.app";

    private final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    AppUpdater(Activity activity) {
        this.activity = activity;
    }

    void check(boolean manual) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(VERSION_URL + "?t=" + System.currentTimeMillis());
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-cache, no-store");
                conn.setRequestProperty("Pragma", "no-cache");
                if (conn.getResponseCode() != 200) throw new IllegalStateException("Update server unavailable");
                StringBuilder b = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) b.append(line);
                }
                JSONObject json = new JSONObject(b.toString());
                long versionCode = json.optLong("version_code", 0);
                String versionName = json.optString("version_name", "New version");
                String downloadUrl = json.optString("download_url", "https://chat.socialrise.garden/downloads/T-Score.apk");
                String sha256 = json.optString("sha256", "").trim().toLowerCase(Locale.US);
                if (versionCode > BuildConfig.VERSION_CODE) {
                    main.post(() -> showUpdate(versionCode, versionName, downloadUrl, sha256));
                } else if (manual) {
                    main.post(() -> Toast.makeText(activity, "T-Score already updated hy", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                if (manual) main.post(() -> Toast.makeText(activity, "Update check failed", Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    private void showUpdate(long versionCode, String versionName, String downloadUrl, String sha256) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("T-Score Update Available")
                .setMessage("Version " + versionName + " available hy.")
                .setNegativeButton("Later", null)
                .setPositiveButton("Update", (d, w) -> startDownload(versionCode, downloadUrl, sha256))
                .show();
    }

    private void startDownload(long versionCode, String downloadUrl, String sha256) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(settings);
            Toast.makeText(activity, "Install unknown apps allow kro, phir Check for Updates dobara tap kro", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            String fileName = "T-Score-update-" + versionCode + ".apk";
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(downloadUrl + (downloadUrl.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis()));
            req.setTitle("T-Score Update");
            req.setDescription("Downloading secure update...");
            req.setMimeType("application/vnd.android.package-archive");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, fileName);
            long id = dm.enqueue(req);
            Toast.makeText(activity, "Update download start hogya", Toast.LENGTH_SHORT).show();

            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent intent) {
                    if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return;
                    try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
                    verifyAndInstall(dm, id, versionCode, sha256);
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else activity.registerReceiver(receiver, filter);
        } catch (Exception e) {
            Toast.makeText(activity, "Update download failed", Toast.LENGTH_LONG).show();
        }
    }

    private void verifyAndInstall(DownloadManager dm, long id, long advertisedVersion, String expectedSha) {
        executor.execute(() -> {
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
            try (android.database.Cursor c = dm.query(q)) {
                if (c == null || !c.moveToFirst()) throw new IllegalStateException("Download not found");
                int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL) throw new IllegalStateException("Download failed");
                String localUri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
                File apk = new File(Uri.parse(localUri).getPath());
                if (!apk.isFile() || apk.length() < 1024) throw new IllegalStateException("Invalid APK");

                PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (info == null || !EXPECTED_PACKAGE.equals(info.packageName)) throw new IllegalStateException("Wrong package");
                long apkVersion = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                if (apkVersion < advertisedVersion || apkVersion <= BuildConfig.VERSION_CODE) throw new IllegalStateException("Stale APK");
                if (!expectedSha.isEmpty() && !expectedSha.equals(sha256(apk))) throw new IllegalStateException("SHA mismatch");

                Uri installUri = dm.getUriForDownloadedFile(id);
                if (installUri == null) throw new IllegalStateException("Installer URI unavailable");
                main.post(() -> {
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(installUri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    activity.startActivity(install);
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(activity, "Downloaded update verify nae hosaka", Toast.LENGTH_LONG).show());
            }
        });
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buf = new byte[8192];
        try (FileInputStream in = new FileInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }
}
