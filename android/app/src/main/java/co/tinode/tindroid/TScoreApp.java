package co.tinode.tindroid;

import android.util.Log;

/**
 * Fail-safe application wrapper for T-Score.
 * Optional startup components must never prevent the app from opening.
 */
public class TScoreApp extends TindroidApp {
    private static final String TAG = "TScoreApp";

    @Override
    public void onCreate() {
        try {
            super.onCreate();
        } catch (Throwable t) {
            // Keep the process alive and let the launcher route to LoginActivity.
            // Individual optional components can be fixed independently without
            // making the entire app unusable.
            Log.e(TAG, "Non-fatal startup initialization failure", t);
        }
    }
}
