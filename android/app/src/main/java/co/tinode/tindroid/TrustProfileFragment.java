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

import java.util.Locale;

public class TrustProfileFragment extends Fragment {
    private LinearLayout verificationList;
    private String username;
    private String email;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_trust_profile, container, false);
        verificationList = root.findViewById(R.id.verificationList);
        renderLocalState();
        loadLiveTrust();
        loadReputation();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (verificationList != null) {
            renderLocalState();
            loadLiveTrust();
            loadReputation();
        }
    }

    private void renderLocalState() {
        if (verificationList == null || getView() == null) return;
        verificationList.removeAllViews();

        username = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(LoginActivity.PREFS_LAST_LOGIN, "T-Score user");
        email = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("tscore_signup_email", "");

        TextView profileName = requireView().findViewById(R.id.profileName);
        TextView profileUsername = requireView().findViewById(R.id.profileUsername);
        Chip trustBadge = requireView().findViewById(R.id.profileTrustBadge);
        TextView trustScoreValue = requireView().findViewById(R.id.trustScoreValue);
        TextView trustScoreStatus = requireView().findViewById(R.id.trustScoreStatus);
        TextView reputationRating = requireView().findViewById(R.id.reputationRating);
        TextView reputationStats = requireView().findViewById(R.id.reputationStats);

        if (profileName != null) profileName.setText(TextUtils.isEmpty(username) ? "T-Score User" : username);
        if (profileUsername != null) profileUsername.setText("@" + (TextUtils.isEmpty(username) ? "tscore-user" : username));
        if (trustBadge != null) trustBadge.setText("LOADING");
        if (trustScoreValue != null) trustScoreValue.setText("— / " + TrustScoreModel.maximumScore());
        if (trustScoreStatus != null) trustScoreStatus.setText("Loading your verified T-Score from server…");
        if (reputationRating != null) reputationRating.setText("Loading reputation…");
        if (reputationStats != null) reputationStats.setText("Deal history is linked to your permanent T-Score user ID.");

        for (TrustScoreModel.Item item : TrustScoreModel.items()) {
            addCheck(item.title, localFallbackStatus(item.title), "+" + item.maxPoints + " points");
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

    private void loadLiveTrust() {
        if (TextUtils.isEmpty(username) || "T-Score user".equals(username)) return;
        final String requestedUser = username;
        TrustApiClient.fetch(requestedUser, new TrustApiClient.Callback() {
            @Override
            public void onSuccess(TrustApiClient.Result result) {
                if (!isAdded() || getView() == null || !requestedUser.equals(username)) return;
                renderLiveResult(result);
            }

            @Override
            public void onError(Exception error) {
                if (!isAdded() || getView() == null || !requestedUser.equals(username)) return;
                Chip badge = requireView().findViewById(R.id.profileTrustBadge);
                TextView status = requireView().findViewById(R.id.trustScoreStatus);
                TextView value = requireView().findViewById(R.id.trustScoreValue);
                if (badge != null) badge.setText("OFFLINE");
                if (value != null) value.setText("— / " + TrustScoreModel.maximumScore());
                if (status != null) status.setText("Could not refresh T-Score. Your account and chats are unaffected.");
            }
        });
    }

    private void loadReputation() {
        String userId = Cache.getTinode().getMyId();
        if (TextUtils.isEmpty(userId) || !userId.startsWith("usr")) return;
        final String requestedId = userId;
        ReputationApiClient.fetchSummary(userId, new ReputationApiClient.SummaryCallback() {
            @Override public void onSuccess(ReputationApiClient.Summary summary) {
                if (!isAdded() || getView() == null || !requestedId.equals(Cache.getTinode().getMyId())) return;
                TextView rating = requireView().findViewById(R.id.reputationRating);
                TextView stats = requireView().findViewById(R.id.reputationStats);
                if (rating != null) {
                    if (summary.reviewCount > 0) rating.setText(String.format(Locale.US, "★ %.1f / 5", summary.averageRating));
                    else rating.setText("No ratings yet");
                }
                if (stats != null) {
                    stats.setText(summary.reviewCount + (summary.reviewCount == 1 ? " review" : " reviews") +
                            " • " + summary.completedDeals + " completed deals" +
                            " • " + summary.disputedDeals + " disputed deals");
                }
            }

            @Override public void onFailure(String message) {
                if (!isAdded() || getView() == null) return;
                TextView rating = requireView().findViewById(R.id.reputationRating);
                TextView stats = requireView().findViewById(R.id.reputationStats);
                if (rating != null) rating.setText("Reputation offline");
                if (stats != null) stats.setText("Reputation will refresh when the T-Score API is available.");
            }
        });
    }

    private void renderLiveResult(TrustApiClient.Result result) {
        Chip badge = requireView().findViewById(R.id.profileTrustBadge);
        TextView value = requireView().findViewById(R.id.trustScoreValue);
        TextView status = requireView().findViewById(R.id.trustScoreStatus);
        if (badge != null) badge.setText(result.badge.toUpperCase());
        if (value != null) value.setText(result.score + " / " + TrustScoreModel.maximumScore());
        if (status != null) status.setText(statusForScore(result.score, result.badge));

        verificationList.removeAllViews();
        for (TrustScoreModel.Item item : TrustScoreModel.items()) {
            String key = keyForTitle(item.title);
            int earned = result.breakdown.containsKey(key) ? result.breakdown.get(key) : 0;
            boolean complete = earned > 0;
            String itemStatus;
            if (complete) {
                itemStatus = "Verified / earned";
            } else if ("Email".equals(item.title) && !TextUtils.isEmpty(email)) {
                itemStatus = "Added · verification pending";
            } else {
                itemStatus = localFallbackStatus(item.title);
            }
            addCheck(item.title, itemStatus, earned + " / " + item.maxPoints + " points");
        }
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
