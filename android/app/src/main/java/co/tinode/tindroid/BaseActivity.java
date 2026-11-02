package co.tinode.tindroid;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Some vendor ROMs throw from edge-to-edge setup even though the same code works
        // on the emulator. UI decoration must never be allowed to hard-close the app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            try {
                EdgeToEdge.enable(this);
            } catch (Throwable t) {
                Log.w(TAG, "Edge-to-edge enable failed; continuing without it", t);
            }
        }

        super.onCreate(savedInstanceState);

        try {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        } catch (Throwable t) {
            Log.w(TAG, "Window edge-to-edge setup failed; continuing", t);
        }
    }

    protected void applyEdgeToEdgeInsets(View rootView) {
        if (rootView == null) {
            return;
        }
        try {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        } catch (Throwable t) {
            Log.w(TAG, "Window inset listener setup failed; continuing", t);
        }
    }
}
