package co.tinode.tindroid;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import co.tinode.tindroid.db.BaseDb;

/**
 * Splash screen on startup.
 */
public class InitRouterActivity extends AppCompatActivity {
    private static final String TAG = "InitRouterActivity";

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

        Class<?> destination = LoginActivity.class;
        try {
            if (BaseDb.getInstance().isReady()) {
                destination = ChatsActivity.class;
            }
        } catch (Throwable t) {
            // A damaged/old local cache must never make the whole app crash.
            // Route to login where the user can continue normally.
            Log.e(TAG, "Local database startup check failed; routing to login", t);
        }

        try {
            Intent launch = new Intent(this, destination);
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launch);
        } catch (Throwable t) {
            Log.e(TAG, "Primary startup route failed; forcing login", t);
            Intent fallback = new Intent(this, LoginActivity.class);
            fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(fallback);
        }
        finish();
    }
}
