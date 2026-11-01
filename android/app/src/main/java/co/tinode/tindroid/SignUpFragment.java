package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
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
import android.widget.Toast;

import java.util.ArrayList;

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
import co.tinode.tinodesdk.model.AuthScheme;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.ServerMessage;

/** Simple and reliable T-Score account registration. */
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
        fragment.findViewById(R.id.signUp).setOnClickListener(this);
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

        final String login = newLogin.getText().toString().trim();
        final String password = newPassword.getText().toString().trim();
        final String fullName = editFullName.getText().toString().trim();
        final String email = editEmail.getText().toString().trim();

        if (login.isEmpty()) { newLogin.setError(getText(R.string.login_required)); return; }
        if (login.contains(":")) { newLogin.setError(getText(R.string.invalid_login)); return; }
        if (password.isEmpty()) { newPassword.setError(getText(R.string.password_required)); return; }
        if (fullName.isEmpty()) { editFullName.setError(getText(R.string.full_name_required)); return; }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.setError(getText(R.string.email_required)); return;
        }

        final Button signUp = (Button) v;
        signUp.setEnabled(false);

        final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(parent);
        @SuppressLint("UnsafeOptInUsageError")
        final String hostName = pref.getString(Utils.PREFS_HOST_NAME, TindroidApp.getDefaultHostName());
        @SuppressLint("UnsafeOptInUsageError")
        final boolean tls = pref.getBoolean(Utils.PREFS_USE_TLS, TindroidApp.getDefaultTLS());

        final Tinode tinode = Cache.getTinode();
        final VxCard card = new VxCard(fullName.length() > Const.MAX_TITLE_LENGTH ?
                fullName.substring(0, Const.MAX_TITLE_LENGTH) : fullName, null);
        final ArrayList<Credential> credentials = new ArrayList<>();
        credentials.add(new Credential("email", email));

        tinode.connect(hostName, tls, false)
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage ignored) {
                        MetaSetDesc<VxCard, String> meta = new MetaSetDesc<>(card, null);
                        return tinode.createAccountBasic(login, password, true, null, meta,
                                credentials.toArray(new Credential[]{}));
                    }
                })
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage msg) {
                        try {
                            UiUtils.updateAndroidAccount(parent, tinode.getMyId(),
                                    AuthScheme.basicInstance(login, password).toString(),
                                    tinode.getAuthToken(), tinode.getAuthTokenExpiration());
                        } catch (Exception e) {
                            Log.w(TAG, "Android account integration failed; continuing signup", e);
                        }
                        parent.runOnUiThread(() -> {
                            try {
                                if (msg != null && msg.ctrl != null && msg.ctrl.code >= 300 &&
                                        msg.ctrl.text != null && msg.ctrl.text.contains("validate credentials")) {
                                    signUp.setEnabled(true);
                                    parent.showFragment(LoginActivity.FRAGMENT_CREDENTIALS, null);
                                } else {
                                    tinode.setAutoLoginToken(tinode.getAuthToken());
                                    UiUtils.onLoginSuccess(parent, signUp, tinode.getMyId());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Post-signup navigation failed", e);
                                signUp.setEnabled(true);
                                Toast.makeText(parent, "Account created. Please sign in.", Toast.LENGTH_LONG).show();
                                parent.showFragment(LoginActivity.FRAGMENT_LOGIN, null);
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
                            signUp.setEnabled(true);
                            if (err instanceof ServerResponseException) {
                                String reason = ((ServerResponseException) err).getReason();
                                if ("auth".equals(reason)) newLogin.setError(getText(R.string.login_rejected));
                                else if ("email".equals(reason)) editEmail.setError(getText(R.string.email_rejected));
                                else Toast.makeText(parent, R.string.error_new_account_failed, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(parent, R.string.error_new_account_failed, Toast.LENGTH_LONG).show();
                            }
                        });
                        return null;
                    }
                });
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) { menu.clear(); }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) { return false; }
}
