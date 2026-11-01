package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.ServerMessage;

public class LoginFragment extends Fragment implements MenuProvider, View.OnClickListener {
    private static final String TAG = "LoginFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setTitle(R.string.app_name);
        }

        View fragment = inflater.inflate(R.layout.fragment_login, container, false);
        View signIn = fragment.findViewById(R.id.signIn);
        View createAccount = fragment.findViewById(R.id.createAccount);
        View forgotPassword = fragment.findViewById(R.id.forgotPassword);
        View updateApp = fragment.findViewById(R.id.updateApp);
        if (signIn != null) signIn.setOnClickListener(this);
        if (createAccount != null) createAccount.setOnClickListener(this);
        if (forgotPassword != null) forgotPassword.setOnClickListener(this);
        if (updateApp != null) updateApp.setOnClickListener(this);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Activity activity = requireActivity();
        ((MenuHost) activity).addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        initBranding(activity);
    }

    @Override
    public void onResume() {
        initBranding(requireActivity());
        super.onResume();
    }

    private void initBranding(Activity activity) {
        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(activity);
        String login = pref.getString(LoginActivity.PREFS_LAST_LOGIN, null);
        View view = getView();
        if (view == null || activity.isFinishing() || activity.isDestroyed()) return;

        if (!TextUtils.isEmpty(login)) {
            TextView loginView = view.findViewById(R.id.editLogin);
            if (loginView != null) loginView.setText(login);
        } else if (UiUtils.isAppFirstRun(activity)) {
            View branding = view.findViewById(R.id.brandingSetup);
            if (branding != null) {
                branding.setVisibility(View.VISIBLE);
                branding.setOnClickListener(v ->
                        ((LoginActivity) activity).showFragment(LoginActivity.FRAGMENT_BRANDING, null));
            }
        } else {
            BrandingConfig config;
            if ((config = BrandingConfig.getConfig(activity)) != null) {
                Bitmap logo = BrandingConfig.getLargeIcon(activity);
                if (logo != null) {
                    AppCompatImageView image = view.findViewById(R.id.imageLogo);
                    TextView title = view.findViewById(R.id.appTitle);
                    if (image != null) image.setImageBitmap(logo);
                    if (title != null) title.setText(config.service_name);
                    View byTinode = view.findViewById(R.id.byTinode);
                    if (byTinode != null) {
                        byTinode.setVisibility(View.VISIBLE);
                        UiUtils.clickToBrowseTinodeURL(byTinode);
                    }
                }
            }
        }
    }

    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) requireActivity();

        if (v.getId() == R.id.createAccount) {
            parent.showFragment(LoginActivity.FRAGMENT_SIGNUP, null);
            return;
        }
        if (v.getId() == R.id.updateApp) {
            AppUpdater.downloadAndInstall(parent);
            return;
        }
        if (v.getId() == R.id.forgotPassword) {
            parent.showFragment(LoginActivity.FRAGMENT_RESET, null);
            return;
        }

        EditText loginInput = parent.findViewById(R.id.editLogin);
        EditText passwordInput = parent.findViewById(R.id.editPassword);
        Button signIn = parent.findViewById(R.id.signIn);
        if (loginInput == null || passwordInput == null || signIn == null) {
            Toast.makeText(parent, "Login form could not be loaded. Please reopen T-Score.", Toast.LENGTH_LONG).show();
            return;
        }

        final String login = loginInput.getText().toString().trim();
        final String password = passwordInput.getText().toString();
        if (login.isEmpty()) {
            loginInput.setError(getText(R.string.login_required));
            return;
        }
        if (password.isEmpty()) {
            passwordInput.setError(getText(R.string.password_required));
            return;
        }

        signIn.setEnabled(false);
        signIn.setText("Signing in...");
        final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(parent);
        @SuppressLint("UnsafeOptInUsageError")
        final String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        final boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode;
        try {
            tinode = Cache.getTinode();
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize T-Score connection", e);
            signIn.setEnabled(true);
            signIn.setText("Sign In");
            Toast.makeText(parent, "Could not start T-Score connection. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            tinode.connect(hostName, tls, false)
                    .thenApply(new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) {
                            return tinode.loginBasic(login, password);
                        }
                    })
                    .thenApply(new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(final ServerMessage msg) {
                            sharedPref.edit().putString(LoginActivity.PREFS_LAST_LOGIN, login).apply();
                            parent.runOnUiThread(() -> {
                                if (parent.isFinishing() || parent.isDestroyed()) return;
                                try {
                                    signIn.setEnabled(true);
                                    signIn.setText("Sign In");
                                    tinode.setAutoLoginToken(tinode.getAuthToken());
                                    // T-Score no longer requires Android AccountManager or contacts sync
                                    // to enter the app. Those integrations are optional and must never
                                    // block authentication or crash login on newer Android devices.
                                    UiUtils.onLoginSuccess(parent, signIn, tinode.getMyId());
                                } catch (Exception e) {
                                    Log.e(TAG, "Post-login navigation failed", e);
                                    signIn.setEnabled(true);
                                    signIn.setText("Sign In");
                                    Toast.makeText(parent, "Signed in, but T-Score could not open chats. Please reopen the app.", Toast.LENGTH_LONG).show();
                                }
                            });
                            return null;
                        }
                    })
                    .thenCatch(new PromisedReply.FailureListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            Log.w(TAG, "Login failed", err);
                            parent.runOnUiThread(() -> {
                                if (parent.isFinishing() || parent.isDestroyed()) return;
                                signIn.setEnabled(true);
                                signIn.setText("Sign In");
                                String message = err.getMessage();
                                Toast.makeText(parent,
                                        message == null ? "Login failed. Check username and password." : "Login failed: " + message,
                                        Toast.LENGTH_LONG).show();
                            });
                            return null;
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Login flow failed before request", e);
            signIn.setEnabled(true);
            signIn.setText("Sign In");
            Toast.makeText(parent, "Could not sign in. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_login, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }
}
