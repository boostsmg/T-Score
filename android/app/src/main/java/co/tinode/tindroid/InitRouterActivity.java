package co.tinode.tindroid;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import co.tinode.tindroid.db.BaseDb;

/**
 * Splash/router activity.
 *
 * SQLite open/migration and Tinode pre-warm are deliberately kept off the UI thread. A damaged,
 * locked or slow local DB must never leave the user staring at the logo indefinitely.
 */
public class InitRouterActivity extends AppCompatActivity {
    private static final String TAG = "InitRouterActivity";
    private static final long STARTUP_TIMEOUT_MS = 2500L;
    private static final ExecutorService STARTUP_EXECUTOR = Executors.newSingleThreadExecutor();

    private final AtomicBoolean mRouted = new AtomicBoolean(false);
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTimeout = () -> {
        Log.w(TAG, "Startup probe exceeded timeout; routing to safe login");
        routeOnce(LoginActivity.class);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                EdgeToEdge.enable(this);
            } catch (Throwable t) {
                Log.w(TAG, "Edge-to-edge init failed; continuing", t);
            }
        }

        super.onCreate(savedInstanceState);

        // Never jump back into a known-crashing state. LoginActivity exposes the saved report.
        final boolean recoveryMode = TScoreApp.hasCrashReport() || !TScoreApp.isStartupHealthy();
        if (recoveryMode) {
            Log.w(TAG, "Previous fatal/startup failure detected; routing to safe login recovery");
            routeOnce(LoginActivity.class);
            return;
        }

        // Guarantee that the splash cannot become an indefinite DB/session loading screen.
        mMainHandler.postDelayed(mTimeout, STARTUP_TIMEOUT_MS);

        STARTUP_EXECUTOR.execute(() -> {
            Class<?> destination = LoginActivity.class;
            try {
                BaseDb db = BaseDb.getInstance();
                if (db.isReady()) {
                    // Pre-warm Tinode/DB off the main thread so ChatsActivity does not pay the
                    // cold-init cost while rendering its first frame.
                    Cache.getTinode();
                    destination = ChatsActivity.class;
                }
            } catch (Throwable t) {
                Log.e(TAG, "Background startup/session probe failed; using login", t);
            }

            final Class<?> finalDestination = destination;
            runOnUiThread(() -> routeOnce(finalDestination));
        });
    }

    private void routeOnce(Class<?> destination) {
        if (!mRouted.compareAndSet(false, true)) {
            return;
        }
        mMainHandler.removeCallbacks(mTimeout);

        try {
            Intent launch = new Intent(this, destination);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launch);
        } catch (Throwable t) {
            Log.e(TAG, "Primary startup route failed; forcing login", t);
            if (destination != LoginActivity.class) {
                try {
                    Intent fallback = new Intent(this, LoginActivity.class);
                    fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(fallback);
                } catch (Throwable fallbackError) {
                    Log.e(TAG, "Unable to launch safe login", fallbackError);
                }
            }
        } finally {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        mMainHandler.removeCallbacks(mTimeout);
        super.onDestroy();
    }
}
