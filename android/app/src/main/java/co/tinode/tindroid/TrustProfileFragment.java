package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

public class TrustProfileFragment extends Fragment {
    private LinearLayout content;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        ScrollView scroll = new ScrollView(requireContext());
        content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(48, 42, 48, 72);
        scroll.addView(content);
        render();
        return scroll;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        content.removeAllViews();
        String username = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(LoginActivity.PREFS_LAST_LOGIN, "T-Score user");
        String email = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("tscore_signup_email", "");

        addText("My T-Score", 28, true);
        addText(TextUtils.isEmpty(username) ? "T-Score user" : "@" + username, 18, false);
        addText("0 / 100", 42, true);
        addText("New profile · Build trust by completing verified checks", 16, false);

        addSection("Verification progress");
        addCheck("Profile photo", "Not verified", "+5 points");
        addCheck("Email", TextUtils.isEmpty(email) ? "Not added" : "Added · verification required", "+5 points");
        addCheck("Phone number", "Not verified", "+10 points");
        addCheck("CNIC / National ID", "Not verified", "+20 points");
        addCheck("Address", "Not verified", "+15 points");
        addCheck("Selfie / liveness", "Not verified", "+10 points");
        addCheck("Account age", "Starts building automatically", "Up to +5 points");
        addCheck("Successful deals", "No completed deals", "Up to +15 points");
        addCheck("Reviews", "No reviews yet", "Up to +10 points");

        addSection("Privacy");
        addText("Your CNIC number, document image and exact address will never be shown publicly. Other users will only see verification badges such as ‘ID Verified’ or ‘Address Verified’.", 15, false);

        Button profile = new Button(requireContext());
        profile.setText("Profile & Verification Settings");
        profile.setOnClickListener(v -> {
            if (getActivity() instanceof ChatsActivity) {
                ((ChatsActivity) getActivity()).showFragment(ChatsActivity.FRAGMENT_ACCOUNT_INFO, null);
            }
        });
        content.addView(profile);
    }

    private void addSection(String value) {
        TextView tv = addText(value, 20, true);
        tv.setPadding(0, 38, 0, 14);
    }

    private void addCheck(String name, String status, String points) {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 22, 28, 22);

        TextView a = new TextView(requireContext());
        a.setText(name + "   " + points);
        a.setTextSize(17);
        a.setTypeface(null, 1);
        box.addView(a);

        TextView b = new TextView(requireContext());
        b.setText(status);
        b.setTextSize(14);
        b.setPadding(0, 6, 0, 0);
        box.addView(b);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 14);
        content.addView(box, lp);
    }

    private TextView addText(String value, int size, boolean bold) {
        TextView tv = new TextView(requireContext());
        tv.setText(value);
        tv.setTextSize(size);
        if (bold) tv.setTypeface(null, 1);
        content.addView(tv);
        return tv;
    }
}
