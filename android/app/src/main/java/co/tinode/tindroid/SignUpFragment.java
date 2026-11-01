package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.ServerMessage;

/** Reliable T-Score account registration with no legacy Android account dependency. */
public class SignUpFragment extends Fragment implements View.OnClickListener, MenuProvider {
    private static final String TAG = "SignUpFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setTitle(R.string.sign_up);
        }

        View fragment = inflater.inflate(R.layout.fragment_signup, container, false);
        View signUp = fragment.findViewById(R.id.signUp);
        if (signUp != null) signUp.setOnClickListener(this);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        LoginActivity parent = (LoginActivity) requireActivity();
        parent.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        View login = view.findViewById(R.id.newLogin);
        if (login != null) login.requestFocus();
    }

    @Override
    public void onClick(View v) {
        final LoginActivity parent = (LoginActivity) requireActivity();
        final View root = getView();
        if (root == null || parent.isFinishing() || parent.isDestroyed()) return;

        final EditText newLogin = root.findViewById(R.id.newLogin);
        final EditText newPassword = root.findViewById(R.id.newPassword);
        final EditText editFullName = root.findViewById(R.id.fullName);
        final EditText editEmail = root.findViewById(R.id.email);
        if (newLogin == null || newPassword == null || editFullName == null || editEmail == null) {
            Toast.makeText(parent, "Signup form could not be loaded. Please reopen T-Score.", Toast.LENGTH_LONG).show();
            return;
        }

        final String login = newLogin.getText().toString().trim();
        final String password = newPassword.getText().toString();
        final String fullName = editFullName.getText().toString().trim();
        final String email = editEmail.getText().toString().trim();

        if (login.isEmpty()) { newLogin.setError(getText(R.string.login_required)); return; }
        if (login.contains(":")) { newLogin.setError(getText(R.string.invalid_login)); return; }
        if (password.isEmpty()) { newPassword.setError(getText(R.string.password_required)); return; }
        if (password.length() < 6) { newPassword.setError("Password must be at least 6 characters"); return; }
        if (fullName.isEmpty()) { editFullName.setError(getText(R.string.full_name_required)); return; }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError(getText(R.string.email_required)); return;
        }

        final Button signUp = (Button) v;
        signUp.setEnabled(false);
        signUp.setText("Creating...");

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(parent);
        @SuppressLint("UnsafeOptInUsageError")
        final String hostName = pref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        final boolean tls = pref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode;
        try {
            tinode = Cache.getTinode();
        } catch (Exception e) {
            Log.e(TAG, "Unable to initialize T-Score connection", e);
            signUp.setEnabled(true);
            signUp.setText("Create Account");
            Toast.makeText(parent, "Could not start T-Score connection. Please try again.", Toast.LENGTH_LONG).show();
            return;
        }

        final String displayName = fullName.length() > Const.MAX_TITLE_LENGTH ?
                fullName.substring(0, Const.MAX_TITLE_LENGTH) : fullName;
        final VxCard card = new VxCard(displayName, null);
        final MetaSetDesc<VxCard, String> meta = new MetaSetDesc<>(card, email);
        final Credential[] credentials = new Credential[]{
                new Credential(Credential.METH_EMAIL, email)
        };

        try {
            tinode.connect(hostName, tls, false)
                    .thenApply(new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) {
                            // Send the email credential because the T-Score server may require
                            // an email credential at account creation time. Omitting it causes
                            // a server-side 422 policy violation.
                            return tinode.createAccountBasic(login, password, false, null, meta,
                                    credentials);
                        }
                    })
                    .thenApply(new PromisedReply.SuccessListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                            pref.edit()
                                    .putString(LoginActivity.PREFS_LAST_LOGIN, login)
                                    .putString("tscore_signup_email", email)
                                    .apply();

                            parent.runOnUiThread(() -> {
                                if (parent.isFinishing() || parent.isDestroyed()) return;
                                signUp.setEnabled(true);
                                signUp.setText("Create Account");
                                Toast.makeText(parent, "Account created successfully. Please sign in.", Toast.LENGTH_LONG).show();
                                try {
                                    parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null);
                                } catch (Exception e) {
                                    Log.e(TAG, "Unable to return to login after signup", e);
                                }
                            });
                            return null;
                        }
                    })
                    .thenCatch(new PromisedReply.FailureListener<>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            Log.w(TAG, "Failed create account", err);
                            parent.runOnUiThread(() -> {
                                if (parent.isFinishing() || parent.isDestroyed()) return;
                                signUp.setEnabled(true);
                                signUp.setText("Create Account");
                                if (err instanceof ServerResponseException) {
                                    String reason = ((ServerResponseException) err).getReason();
                                    String message = err.getMessage();
                                    if ("auth".equals(reason)) {
                                        newLogin.setError(getText(R.string.login_rejected));
                                    } else if (message != null && message.toLowerCase().contains("duplicate")) {
                                        newLogin.setError("Username already exists");
                                    } else {
                                        Toast.makeText(parent, "Account could not be created: " +
                                                (message == null ? "server rejected request" : message), Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    Toast.makeText(parent, "Could not create account. Check internet and try again.", Toast.LENGTH_LONG).show();
                                }
                            });
                            return null;
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Signup flow failed before request", e);
            signUp.setEnabled(true);
            signUp.setText("Create Account");
            Toast.makeText(parent, "Could not create account. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) { menu.clear(); }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) { return false; }
}
