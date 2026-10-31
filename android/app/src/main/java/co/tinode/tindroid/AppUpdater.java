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

public final class AppUpdater {
    private static final String APK_URL = "https://chat.socialrise.garden/downloads/T-Score.apk";

    private AppUpdater() {}

    public static void downloadAndInstall(Activity activity) {
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

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(APK_URL))
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
