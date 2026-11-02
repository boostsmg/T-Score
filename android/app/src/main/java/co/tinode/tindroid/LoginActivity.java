package co.tinode.tindroid;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.db.BaseDb;

/**
 * LoginActivity is a FrameLayout which switches between fragments.
 */
public class LoginActivity extends BaseActivity
        implements ImageViewFragment.AvatarCompletionHandler {
    private static final String TAG = "LoginActivity";

    public static final String EXTRA_CONFIRM_CREDENTIALS = "confirmCredentials";
    public static final String EXTRA_ADDING_ACCOUNT = "addNewAccount";
    static final String FRAGMENT_LOGIN = "login";
    static final String FRAGMENT_SIGNUP = "signup";
    static final String FRAGMENT_SETTINGS = "settings";
    static final String FRAGMENT_RESET = "reset";
    static final String FRAGMENT_CREDENTIALS = "cred";
    static final String FRAGMENT_AVATAR_PREVIEW = "avatar_preview";
    static final String FRAGMENT_BRANDING = "branding";
    static final String PREFS_LAST_LOGIN = "pref_lastLogin";

    private AvatarViewModel mAvatarVM;

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.setupSystemToolbar(this);
        setContentView(R.layout.activity_login);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));
        PreferenceManager.setDefaultValues(this, R.xml.login_preferences, false);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> getSupportFragmentManager().popBackStack());

        final String crashReport = TScoreApp.peekCrashReport();
        final boolean recoveryMode = !TextUtils.isEmpty(crashReport) || !TScoreApp.isStartupHealthy();

        boolean dbReady = false;
        boolean credentialsRequired = false;
        if (!recoveryMode) {
            try {
                BaseDb db = BaseDb.getInstance();
                dbReady = db.isReady();
                credentialsRequired = db.isCredValidationRequired();
            } catch (Throwable t) {
                Log.e(TAG, "Database state check failed; staying on safe login", t);
            }
        }

        if (dbReady) {
            Intent intent = new Intent(this, ChatsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return;
        }

        try {
            showFragment(credentialsRequired ? FRAGMENT_CREDENTIALS : FRAGMENT_LOGIN,
                    null, false);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to render requested login fragment; forcing clean login fragment", t);
            try {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.contentFragment, new LoginFragment(), FRAGMENT_LOGIN)
                        .commitAllowingStateLoss();
            } catch (Throwable fallbackError) {
                Log.e(TAG, "Clean login fallback failed", fallbackError);
            }
        }

        mAvatarVM = new ViewModelProvider(this).get(AvatarViewModel.class);

        if (!TextUtils.isEmpty(crashReport)) {
            showCrashReport(crashReport);
        }
    }

    private void showCrashReport(String report) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("T-Score crash detected")
                    .setMessage("App ne pichli dafa fatal error detect kiya tha. 'Copy Report' press karke report copy kro aur ChatGPT ko send kro.\n\n" + report)
                    .setCancelable(false)
                    .setNegativeButton("Copy Report", (dialog, which) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("T-Score crash report", report));
                            Toast.makeText(this, "Crash report copied", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setPositiveButton("Continue Safe", (dialog, which) -> {
                        TScoreApp.clearCrashReport();
                        dialog.dismiss();
                    })
                    .show();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to show crash report dialog", t);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            UiUtils.setupToolbar(this, null, null, false, null, false, 0);
        } catch (Throwable t) {
            Log.w(TAG, "Toolbar setup failed during login resume", t);
        }
    }

    void reportError(final Exception err, final Button button, final int attachTo, final int errId) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        String message = getText(errId).toString();
        String errMessage = err != null ? err.getMessage() : "";

        if (err != null) {
            Throwable cause = err;
            while ((cause = cause.getCause()) != null) {
                errMessage = cause.getMessage();
            }
        }
        final String finalMessage = message +
                (!TextUtils.isEmpty(errMessage) ? (": " + errMessage) : "");
        Log.w(TAG, finalMessage, err);

        runOnUiThread(() -> {
            if (button != null) {
                button.setEnabled(true);
            }
            EditText field = attachTo != 0 ? (EditText) findViewById(attachTo) : null;
            if (field != null) {
                field.setError(finalMessage);
            } else {
                Toast.makeText(LoginActivity.this, finalMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            showFragment(FRAGMENT_SETTINGS, null);
            return true;
        } else if (id == R.id.action_signup) {
            showFragment(FRAGMENT_SIGNUP, null);
            return true;
        } else if (id == R.id.action_about) {
            DialogFragment about = new AboutDialogFragment();
            about.show(getSupportFragmentManager(), "about");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    void showFragment(String tag, Bundle args) {
        showFragment(tag, args, true);
    }

    private void showFragment(String tag, Bundle args, Boolean addToBackstack) {
        if (isFinishing() || isDestroyed() || TextUtils.isEmpty(tag)) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_LOGIN:
                    fragment = new LoginFragment();
                    break;
                case FRAGMENT_SETTINGS:
                    fragment = new LoginSettingsFragment();
                    break;
                case FRAGMENT_SIGNUP:
                    fragment = new SignUpFragment();
                    break;
                case FRAGMENT_RESET:
                    fragment = new PasswordResetFragment();
                    break;
                case FRAGMENT_CREDENTIALS:
                    fragment = new CredentialsFragment();
                    break;
                case FRAGMENT_AVATAR_PREVIEW:
                    fragment = new ImageViewFragment();
                    if (args == null) {
                        args = new Bundle();
                    }
                    args.putBoolean(AttachmentHandler.ARG_AVATAR, true);
                    break;
                case FRAGMENT_BRANDING:
                    fragment = new BrandingFragment();
                    break;
                default:
                    Log.w(TAG, "Ignoring unknown login fragment tag: " + tag);
                    fragment = new LoginFragment();
                    tag = FRAGMENT_LOGIN;
                    break;
            }
        }

        // args may legitimately be null. The old code called Bundle.putAll(null) on a
        // restored fragment, which can hard-close the activity on some restore paths.
        if (args != null) {
            if (fragment.getArguments() != null) {
                fragment.getArguments().putAll(args);
            } else {
                fragment.setArguments(args);
            }
        }

        FragmentTransaction tx = fm.beginTransaction()
                .replace(R.id.contentFragment, fragment, tag)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        if (addToBackstack) {
            tx = tx.addToBackStack(null);
        }
        tx.commitAllowingStateLoss();
    }

    @Override
    public void onAcceptAvatar(String topicName, Bitmap avatar) {
        if (isFinishing() || isDestroyed() || mAvatarVM == null) {
            return;
        }
        mAvatarVM.setAvatar(avatar);
    }
}
