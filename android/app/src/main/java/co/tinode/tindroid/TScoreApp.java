package co.tinode.tindroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * T-Score application wrapper with real-device crash capture.
 *
 * A core startup failure must never be silently ignored: doing so leaves Cache/BaseDb/Tinode
 * partially initialized and causes a later hard-close which is much harder to diagnose.
 */
public class TScoreApp extends TindroidApp {
    private static final String TAG = "TScoreApp";
    private static final String CRASH_PREFS = "tscore_crash_report";
    private static final String KEY_LAST_CRASH = "last_crash";
    private static final String KEY_CRASH_VERSION_CODE = "crash_version_code";
    private static volatile boolean sStartupHealthy = true;

    @Override
    public void onCreate() {
        installCrashCapture();

        try {
            super.onCreate();
            sStartupHealthy = true;
        } catch (Throwable t) {
            // Do not pretend the app initialized successfully. Persist the exact cause and let
            // InitRouterActivity/LoginActivity enter a safe diagnostic path which does not jump
            // directly back into ChatsActivity.
            sStartupHealthy = false;
            saveCrash("STARTUP", Thread.currentThread(), t);
            Log.e(TAG, "Core startup initialization failed; entering safe recovery mode", t);
        }
    }

    private void installCrashCapture() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                saveCrash("UNCAUGHT", thread, throwable);
            } catch (Throwable ignored) {
                // Crash reporting itself must never replace the original exception.
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                System.exit(10);
            }
        });
    }

    private void saveCrash(String kind, Thread thread, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        if (throwable != null) {
            throwable.printStackTrace(pw);
        }
        pw.flush();

        String report = "T-Score crash report\n"
                + "kind=" + kind + "\n"
                + "version=" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                + "device=" + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT + "\n"
                + "thread=" + (thread == null ? "unknown" : thread.getName()) + "\n"
                + "time=" + System.currentTimeMillis() + "\n\n"
                + sw;

        try {
            getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, report)
                    .putInt(KEY_CRASH_VERSION_CODE, BuildConfig.VERSION_CODE)
                    .commit();
        } catch (Throwable e) {
            Log.e(TAG, "Unable to persist crash report", e);
        }
    }

    public static boolean isStartupHealthy() {
        return sStartupHealthy;
    }

    public static boolean hasCrashReport() {
        Context context = getAppContext();
        return context != null && !getRelevantCrashReport(context).isEmpty();
    }

    public static String peekCrashReport() {
        Context context = getAppContext();
        return context == null ? "" : getRelevantCrashReport(context);
    }

    private static String getRelevantCrashReport(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
            String report = prefs.getString(KEY_LAST_CRASH, "");
            if (report == null || report.isEmpty()) {
                return "";
            }

            int reportVersionCode = prefs.getInt(KEY_CRASH_VERSION_CODE, -1);
            if (reportVersionCode <= 0) {
                reportVersionCode = parseVersionCode(report);
            }

            // Crash reports are only actionable for the exact build which produced them.
            // In particular, do not keep showing a v1.0.108 Firebase crash after the user
            // installs a newer build where that path has already been fixed.
            if (reportVersionCode > 0 && reportVersionCode != BuildConfig.VERSION_CODE) {
                prefs.edit()
                        .remove(KEY_LAST_CRASH)
                        .remove(KEY_CRASH_VERSION_CODE)
                        .apply();
                Log.i(TAG, "Discarded stale crash report from build " + reportVersionCode
                        + " after update to " + BuildConfig.VERSION_CODE);
                return "";
            }

            return report;
        } catch (Throwable t) {
            Log.w(TAG, "Unable to read crash report", t);
            return "";
        }
    }

    private static int parseVersionCode(String report) {
        if (report == null) {
            return -1;
        }
        try {
            int versionLine = report.indexOf("version=");
            if (versionLine < 0) {
                return -1;
            }
            int open = report.indexOf('(', versionLine);
            int close = open < 0 ? -1 : report.indexOf(')', open + 1);
            if (open < 0 || close <= open + 1) {
                return -1;
            }
            return Integer.parseInt(report.substring(open + 1, close).trim());
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static void clearCrashReport() {
        Context context = getAppContext();
        if (context == null) {
            return;
        }
        try {
            context.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_LAST_CRASH)
                    .remove(KEY_CRASH_VERSION_CODE)
                    .apply();
        } catch (Throwable ignored) {
        }
    }
}
