package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.chip.Chip;

public class TrustProfileFragment extends Fragment {
    private LinearLayout verificationList;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_trust_profile, container, false);
        verificationList = root.findViewById(R.id.verificationList);
        render();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (verificationList != null) render();
    }

    private void render() {
        if (verificationList == null || getView() == null) return;
        verificationList.removeAllViews();

        String username = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(LoginActivity.PREFS_LAST_LOGIN, "T-Score user");
        String email = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("tscore_signup_email", "");

        TextView profileName = requireView().findViewById(R.id.profileName);
        TextView profileUsername = requireView().findViewById(R.id.profileUsername);
        Chip trustBadge = requireView().findViewById(R.id.profileTrustBadge);
        TextView trustScoreValue = requireView().findViewById(R.id.trustScoreValue);
        TextView trustScoreStatus = requireView().findViewById(R.id.trustScoreStatus);

        if (profileName != null) profileName.setText(TextUtils.isEmpty(username) ? "T-Score User" : username);
        if (profileUsername != null) profileUsername.setText("@" + (TextUtils.isEmpty(username) ? "tscore-user" : username));
        if (trustBadge != null) trustBadge.setText("NEW PROFILE");
        if (trustScoreValue != null) trustScoreValue.setText("0 / " + TrustScoreModel.maximumScore());
        if (trustScoreStatus != null) {
            trustScoreStatus.setText("Your score will be calculated by T-Score servers after verification and deal activity.");
        }

        for (TrustScoreModel.Item item : TrustScoreModel.items()) {
            String status;
            if ("Email".equals(item.title)) {
                status = TextUtils.isEmpty(email) ? "Not added" : "Added · verification pending";
            } else if ("Account age".equals(item.title)) {
                status = "Builds automatically over time";
            } else if ("Successful deals".equals(item.title)) {
                status = "No completed deals yet";
            } else if ("Reviews".equals(item.title)) {
                status = "No reviews yet";
            } else if ("Account standing".equals(item.title)) {
                status = "Good standing builds automatically";
            } else if ("Profile Photo".equals(item.title)) {
                status = "Add and verify your real photo";
            } else {
                status = "Not verified";
            }
            addCheck(item.title, status, "+" + item.maxPoints + " points");
        }

        Button profile = requireView().findViewById(R.id.profileSettingsButton);
        if (profile != null) {
            profile.setText("Continue verification");
            profile.setOnClickListener(v -> {
                if (getActivity() instanceof ChatsActivity) {
                    ((ChatsActivity) getActivity()).showFragment(ChatsActivity.FRAGMENT_ACCOUNT_INFO, null);
                }
            });
        }
    }

    private void addCheck(String name, String status, String points) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_verification_check, verificationList, false);
        TextView title = v.findViewById(R.id.verifyTitle);
        TextView stat = v.findViewById(R.id.verifyStatus);
        TextView pts = v.findViewById(R.id.verifyPoints);
        if (title != null) title.setText(name);
        if (stat != null) stat.setText(status);
        if (pts != null) pts.setText(points);
        verificationList.addView(v);
    }
}
