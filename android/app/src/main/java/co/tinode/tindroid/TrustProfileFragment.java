package co.tinode.tindroid;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.chip.Chip;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TrustProfileFragment extends Fragment {
    private LinearLayout verificationList;
    private String username;
    private String userId;
    private String email;
    private final Map<String,String> verificationStates = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_trust_profile, container, false);
        verificationList = root.findViewById(R.id.verificationList);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refreshProfile();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null && verificationList != null) {
            refreshProfile();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        verificationList = null;
    }

    private void refreshProfile() {
        if (!isAdded() || getView() == null || verificationList == null) return;
        renderLocalState();
        loadLiveTrust();
        loadReputation();
        loadVerificationRequests();
    }

    private void renderLocalState() {
        View root = getView();
        if (verificationList == null || root == null) return;
        verificationList.removeAllViews();

        username = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(LoginActivity.PREFS_LAST_LOGIN, "T-Score user");
        email = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("tscore_signup_email", "");
        try {
            userId = Cache.getTinode().getMyId();
        } catch (Throwable ignored) {
            userId = null;
        }

        TextView profileName = root.findViewById(R.id.profileName);
        TextView profileUsername = root.findViewById(R.id.profileUsername);
        Chip trustBadge = root.findViewById(R.id.profileTrustBadge);
        TextView trustScoreValue = root.findViewById(R.id.trustScoreValue);
        TextView trustScoreStatus = root.findViewById(R.id.trustScoreStatus);
        TextView reputationRating = root.findViewById(R.id.reputationRating);
        TextView reputationStats = root.findViewById(R.id.reputationStats);

        if (profileName != null) profileName.setText(TextUtils.isEmpty(username) ? "T-Score User" : username);
        if (profileUsername != null) profileUsername.setText("@" + (TextUtils.isEmpty(username) ? "tscore-user" : username));
        if (trustBadge != null) trustBadge.setText("LOADING");
        if (trustScoreValue != null) trustScoreValue.setText("— / " + TrustScoreModel.maximumScore());
        if (trustScoreStatus != null) trustScoreStatus.setText("Loading your verified T-Score from server…");
        if (reputationRating != null) reputationRating.setText("Loading reputation…");
        if (reputationStats != null) reputationStats.setText("Deal history is linked to your permanent T-Score user ID.");

        for (TrustScoreModel.Item item : TrustScoreModel.items()) {
            addCheck(item.title, requestAwareStatus(item.title, localFallbackStatus(item.title)), "+" + item.maxPoints + " points");
        }

        Button profile = root.findViewById(R.id.profileSettingsButton);
        if (profile != null) {
            profile.setText("Manage verification");
            profile.setOnClickListener(v -> showVerificationManager());
        }
    }

    private void loadLiveTrust() {
        if (TextUtils.isEmpty(userId) || !userId.startsWith("usr")) return;
        final String requestedId = userId;
        TrustApiClient.fetch(requestedId, new TrustApiClient.Callback() {
            @Override public void onSuccess(TrustApiClient.Result result) {
                if (!isAdded() || getView() == null || !requestedId.equals(userId)) return;
                renderLiveResult(result);
            }
            @Override public void onError(Exception error) {
                View root = getView();
                if (!isAdded() || root == null || !requestedId.equals(userId)) return;
                Chip badge = root.findViewById(R.id.profileTrustBadge);
                TextView status = root.findViewById(R.id.trustScoreStatus);
                TextView value = root.findViewById(R.id.trustScoreValue);
                if (badge != null) badge.setText("OFFLINE");
                if (value != null) value.setText("— / " + TrustScoreModel.maximumScore());
                if (status != null) status.setText("Could not refresh T-Score. Your account and chats are unaffected.");
            }
        });
    }

    private void loadVerificationRequests() {
        VerificationApiClient.list(new VerificationApiClient.ListCallback() {
            @Override public void onSuccess(List<VerificationApiClient.Item> items) {
                if (!isAdded() || getView() == null) return;
                verificationStates.clear();
                for (VerificationApiClient.Item item : items) {
                    if (!verificationStates.containsKey(item.type) || "APPROVED".equals(item.status)) {
                        verificationStates.put(item.type, item.status);
                    }
                }
                renderLocalState();
                loadLiveTrust();
            }
            @Override public void onFailure(String message) { }
        });
    }

    private void showVerificationManager() {
        final String[] labels = {"Email", "Phone number", "CNIC / National ID", "Address", "Selfie / liveness", "Profile photo"};
        final String[] types = {"EMAIL", "PHONE", "IDENTITY", "ADDRESS", "LIVENESS", "PROFILE_PHOTO"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Choose verification")
                .setItems(labels, (dialog, which) -> startVerification(labels[which], types[which]))
                .setNegativeButton("Close", null)
                .show();
    }

    private void startVerification(String label, String type) {
        String existing = verificationStates.get(type);
        if ("APPROVED".equals(existing)) {
            Toast.makeText(requireContext(), label + " is already verified", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("PENDING".equals(existing)) {
            Toast.makeText(requireContext(), label + " verification is already pending", Toast.LENGTH_SHORT).show();
            return;
        }
        VerificationApiClient.create(type, new VerificationApiClient.ActionCallback() {
            @Override public void onSuccess(boolean evidenceRequired, String requestId) {
                if (!isAdded() || getView() == null) return;
                verificationStates.put(type, "PENDING");
                renderLocalState();
                String message = evidenceRequired
                        ? "Request started. Private evidence review is required before approval."
                        : "Verification request started successfully.";
                new AlertDialog.Builder(requireContext()).setTitle(label).setMessage(message).setPositiveButton("OK", null).show();
            }
            @Override public void onFailure(String message) {
                if (isAdded()) Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadReputation() {
        if (TextUtils.isEmpty(userId) || !userId.startsWith("usr")) return;
        final String requestedId = userId;
        ReputationApiClient.fetchSummary(requestedId, new ReputationApiClient.SummaryCallback() {
            @Override public void onSuccess(ReputationApiClient.Summary summary) {
                View root = getView();
                if (!isAdded() || root == null || !requestedId.equals(userId)) return;
                TextView rating = root.findViewById(R.id.reputationRating);
                TextView stats = root.findViewById(R.id.reputationStats);
                if (rating != null) rating.setText(summary.reviewCount > 0 ? String.format(Locale.US, "★ %.1f / 5", summary.averageRating) : "No ratings yet");
                if (stats != null) stats.setText(summary.reviewCount + (summary.reviewCount == 1 ? " review" : " reviews") + " • " + summary.completedDeals + " completed deals" + " • " + summary.disputedDeals + " disputed deals");
            }
            @Override public void onFailure(String message) {
                View root = getView();
                if (!isAdded() || root == null) return;
                TextView rating = root.findViewById(R.id.reputationRating);
                TextView stats = root.findViewById(R.id.reputationStats);
                if (rating != null) rating.setText("Reputation offline");
                if (stats != null) stats.setText("Reputation will refresh when the T-Score API is available.");
            }
        });
    }

    private void renderLiveResult(TrustApiClient.Result result) {
        View root = getView();
        if (root == null || verificationList == null) return;
        Chip badge = root.findViewById(R.id.profileTrustBadge);
        TextView value = root.findViewById(R.id.trustScoreValue);
        TextView status = root.findViewById(R.id.trustScoreStatus);
        if (badge != null) badge.setText(result.badge.toUpperCase());
        if (value != null) value.setText(result.score + " / " + TrustScoreModel.maximumScore());
        if (status != null) status.setText(statusForScore(result.score, result.badge));

        verificationList.removeAllViews();
        for (TrustScoreModel.Item item : TrustScoreModel.items()) {
            String key = keyForTitle(item.title);
            int earned = result.breakdown.containsKey(key) ? result.breakdown.get(key) : 0;
            String itemStatus;
            if ("Account standing".equals(item.title) && !"GOOD".equals(result.standing)) itemStatus = result.standing;
            else if (earned > 0) itemStatus = "Verified / earned";
            else if ("Email".equals(item.title) && !TextUtils.isEmpty(email)) itemStatus = "Added · verification pending";
            else itemStatus = requestAwareStatus(item.title, localFallbackStatus(item.title));
            addCheck(item.title, itemStatus, earned + " / " + item.maxPoints + " points");
        }
    }

    private String requestAwareStatus(String title, String fallback) {
        String type = typeForTitle(title);
        if (type == null) return fallback;
        String state = verificationStates.get(type);
        if ("APPROVED".equals(state)) return "Approved verification";
        if ("PENDING".equals(state)) return "Verification pending";
        if ("REJECTED".equals(state)) return "Rejected · you can submit again";
        return fallback;
    }

    private String typeForTitle(String title) {
        if ("Profile Photo".equals(title)) return "PROFILE_PHOTO";
        if ("Email".equals(title)) return "EMAIL";
        if ("Phone number".equals(title)) return "PHONE";
        if ("CNIC / National ID".equals(title)) return "IDENTITY";
        if ("Address".equals(title)) return "ADDRESS";
        if ("Selfie / liveness".equals(title)) return "LIVENESS";
        return null;
    }

    private String statusForScore(int score, String badge) {
        if (score <= 0) return "Start verification and trusted deal activity to build your T-Score.";
        return badge + " · score verified by T-Score server.";
    }

    private String localFallbackStatus(String title) {
        if ("Email".equals(title)) return TextUtils.isEmpty(email) ? "Not added" : "Added · verification pending";
        if ("Account age".equals(title)) return "Builds automatically over time";
        if ("Successful deals".equals(title)) return "No completed deals yet";
        if ("Reviews".equals(title)) return "No reviews yet";
        if ("Account standing".equals(title)) return "Good standing builds automatically";
        if ("Profile Photo".equals(title)) return "Add and verify your real photo";
        return "Not verified";
    }

    private String keyForTitle(String title) {
        if ("Profile Photo".equals(title)) return "profile_photo";
        if ("Email".equals(title)) return "email";
        if ("Phone number".equals(title)) return "phone";
        if ("CNIC / National ID".equals(title)) return "identity";
        if ("Address".equals(title)) return "address";
        if ("Selfie / liveness".equals(title)) return "liveness";
        if ("Account age".equals(title)) return "account_age";
        if ("Successful deals".equals(title)) return "successful_deals";
        if ("Reviews".equals(title)) return "reviews";
        return "account_standing";
    }

    private void addCheck(String name, String status, String points) {
        if (!isAdded() || verificationList == null) return;
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_verification_check, verificationList, false);
        TextView title = v.findViewById(R.id.verifyTitle);
        TextView stat = v.findViewById(R.id.verifyStatus);
        TextView pts = v.findViewById(R.id.verifyPoints);
        if (title != null) title.setText(name);
        if (stat != null) stat.setText(status);
        if (pts != null) pts.setText(points);
        verificationList.addView(v);
    }
}
