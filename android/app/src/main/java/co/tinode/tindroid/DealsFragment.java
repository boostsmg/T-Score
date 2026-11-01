package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DealsFragment extends Fragment {
    private RecyclerView dealsList;
    private View emptyState;
    private Chip chipActive;
    private Chip chipCompleted;
    private Chip chipDisputed;
    private DealsAdapter adapter;
    private List<DealDraft> localDrafts = new ArrayList<>();
    private List<DealApiClient.RemoteDeal> remoteDeals = new ArrayList<>();

    private final ActivityResultLauncher<Intent> personPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                String userId = result.getData().getStringExtra(StartChatActivity.EXTRA_SELECTED_USER_ID);
                if (!TextUtils.isEmpty(userId)) showCreateDealDialog(userId);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_deals, container, false);

        TextView emptyTitle = root.findViewById(R.id.emptyTitle);
        TextView emptySubtitle = root.findViewById(R.id.emptySubtitle);
        if (emptyTitle != null) emptyTitle.setText("No deals yet");
        if (emptySubtitle != null) emptySubtitle.setText("Choose a T-Score user, agree on clear terms, then start a trusted deal.");

        chipActive = root.findViewById(R.id.dealChipActive);
        chipCompleted = root.findViewById(R.id.dealChipCompleted);
        chipDisputed = root.findViewById(R.id.dealChipDisputed);
        dealsList = root.findViewById(R.id.dealsList);
        emptyState = root.findViewById(R.id.dealsEmptyState);

        if (dealsList != null) {
            dealsList.setLayoutManager(new LinearLayoutManager(requireContext()));
            adapter = new DealsAdapter();
            dealsList.setAdapter(adapter);
        }

        Button start = root.findViewById(R.id.startDealButton);
        if (start != null) {
            start.setText("Select person & start deal");
            start.setOnClickListener(v -> launchPersonPicker());
        }

        refresh();
        return root;
    }

    private void launchPersonPicker() {
        Intent intent = new Intent(requireContext(), StartChatActivity.class);
        intent.putExtra(StartChatActivity.EXTRA_SELECT_FOR_DEAL, true);
        personPicker.launch(intent);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) refresh();
    }

    private void refresh() {
        localDrafts = DealDraftStore.load(requireContext());
        updateUi();
        DealApiClient.list(new DealApiClient.ListCallback() {
            @Override
            public void onSuccess(List<DealApiClient.RemoteDeal> deals) {
                if (!isAdded()) return;
                remoteDeals = deals;
                updateUi();
            }

            @Override
            public void onFailure(String message) {
                // Keep local drafts usable when the API is not deployed/reachable yet.
            }
        });
    }

    private void updateUi() {
        if (adapter != null) adapter.setItems(localDrafts, remoteDeals);
        boolean empty = localDrafts.isEmpty() && remoteDeals.isEmpty();
        if (dealsList != null) dealsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);

        int active = localDrafts.size();
        int completed = 0;
        int disputed = 0;
        for (DealApiClient.RemoteDeal deal : remoteDeals) {
            if ("COMPLETED".equals(deal.status)) completed++;
            else if ("DISPUTED".equals(deal.status)) disputed++;
            else if (!"CANCELLED".equals(deal.status)) active++;
        }
        if (chipActive != null) chipActive.setText(active + " Active");
        if (chipCompleted != null) chipCompleted.setText(completed + " Completed");
        if (chipDisputed != null) chipDisputed.setText(disputed + " Disputed");
    }

    private void showCreateDealDialog(String selectedUserId) {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_deal, null, false);
        EditText counterpart = form.findViewById(R.id.dealCounterpartInput);
        EditText title = form.findViewById(R.id.dealTitleInput);
        EditText amount = form.findViewById(R.id.dealAmountInput);
        EditText currency = form.findViewById(R.id.dealCurrencyInput);
        EditText terms = form.findViewById(R.id.dealTermsInput);
        RadioButton buyer = form.findViewById(R.id.dealRoleBuyer);

        if (!TextUtils.isEmpty(selectedUserId)) {
            counterpart.setText(selectedUserId);
            counterpart.setEnabled(false);
            counterpart.setHint("Selected T-Score user ID");
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save Draft", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String other = cleanCounterpart(counterpart.getText().toString());
            String selfLogin = cleanCounterpart(PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(LoginActivity.PREFS_LAST_LOGIN, ""));
            String selfUid = Cache.getTinode().getMyId();
            String dealTitle = title.getText().toString().trim();
            String dealAmount = amount.getText().toString().trim();
            String dealCurrency = currency.getText().toString().trim().toUpperCase(Locale.US);
            String dealTerms = terms.getText().toString().trim();
            String role = buyer.isChecked() ? "BUYER" : "SELLER";

            if (TextUtils.isEmpty(other)) { counterpart.setError("Choose another T-Score user"); return; }
            if ((!TextUtils.isEmpty(selfUid) && selfUid.equals(other)) ||
                    (!TextUtils.isEmpty(selfLogin) && selfLogin.equals(other))) {
                counterpart.setError("Choose another user"); return;
            }
            if (dealTitle.length() < 3) { title.setError("Add a clear deal title"); return; }
            if (TextUtils.isEmpty(dealAmount)) { amount.setError("Amount required"); return; }
            try { if (Double.parseDouble(dealAmount) <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { amount.setError("Enter a valid amount"); return; }
            if (dealCurrency.length() != 3) { currency.setError("Use 3-letter currency, e.g. PKR"); return; }
            if (dealTerms.length() < 3) { terms.setError("Add basic deal terms"); return; }

            DealDraftStore.add(requireContext(), DealDraft.create(other, role, dealTitle, dealAmount, dealCurrency, dealTerms));
            dialog.dismiss();
            refresh();
            Toast.makeText(requireContext(), "Deal draft saved for selected user", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private String cleanCounterpart(String value) {
        String out = value == null ? "" : value.trim();
        if (out.startsWith("usr")) return out;
        while (out.startsWith("@")) out = out.substring(1);
        return out.toLowerCase(Locale.US);
    }

    private String displayCounterpart(String value) {
        if (TextUtils.isEmpty(value)) return "Unknown user";
        return value.startsWith("usr") ? value : "@" + value;
    }

    private void showDraftDetails(DealDraft draft) {
        String roleText = "BUYER".equals(draft.role) ? "You are the buyer" : "You are the seller";
        String body = roleText + "\nOther user: " + displayCounterpart(draft.counterpart) +
                "\nAmount: " + draft.currency + " " + draft.amount +
                "\n\nTerms:\n" + draft.terms +
                "\n\nStatus: Draft • not sent yet";

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(draft.title)
                .setMessage(body)
                .setNegativeButton("Close", null)
                .setNeutralButton("Delete Draft", null)
                .setPositiveButton("Send Deal", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                confirmDelete(draft);
            });
            Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (draft.counterpart == null || !draft.counterpart.startsWith("usr")) {
                send.setEnabled(false);
                send.setText("Select verified user first");
                return;
            }
            send.setOnClickListener(v -> {
                send.setEnabled(false);
                send.setText("Sending…");
                DealApiClient.create(draft, new DealApiClient.Callback() {
                    @Override public void onSuccess(String dealId) {
                        if (!isAdded()) return;
                        DealDraftStore.remove(requireContext(), draft.id);
                        dialog.dismiss();
                        refresh();
                        Toast.makeText(requireContext(), "Deal sent successfully", Toast.LENGTH_LONG).show();
                    }
                    @Override public void onFailure(String message) {
                        if (!isAdded()) return;
                        send.setEnabled(true);
                        send.setText("Send Deal");
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
        dialog.show();
    }

    private void showRemoteDetails(DealApiClient.RemoteDeal deal) {
        String me = Cache.getTinode().getMyId();
        String role = me != null && me.equals(deal.buyerId) ? "Buyer" : "Seller";
        String body = "Your role: " + role +
                "\nOther user: " + displayCounterpart(deal.counterpartFor(me)) +
                "\nAmount: " + deal.currency + " " + deal.amountDisplay() +
                "\nStatus: " + prettyStatus(deal.status) +
                "\n\nTerms:\n" + deal.terms +
                "\n\nDeal ID: " + deal.id;

        Map<String,String> actions = availableActions(deal, me);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(deal.title)
                .setMessage(body)
                .setNegativeButton("Close", null)
                .setPositiveButton(actions.isEmpty() ? "OK" : "Deal Actions", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (actions.isEmpty()) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                showActionPicker(deal, actions);
            });
        });
        dialog.show();
    }

    private Map<String,String> availableActions(DealApiClient.RemoteDeal deal, String me) {
        Map<String,String> actions = new LinkedHashMap<>();
        if (TextUtils.isEmpty(me)) return actions;
        switch (deal.status) {
            case "PROPOSED":
                if (!me.equals(deal.createdBy)) actions.put("Accept deal", "ACCEPTED");
                actions.put("Cancel deal", "CANCELLED");
                actions.put("Open dispute", "DISPUTED");
                break;
            case "ACCEPTED":
                if (me.equals(deal.sellerId)) actions.put("Start work", "IN_PROGRESS");
                actions.put("Cancel deal", "CANCELLED");
                actions.put("Open dispute", "DISPUTED");
                break;
            case "IN_PROGRESS":
                if (me.equals(deal.sellerId)) actions.put("Mark delivered", "DELIVERED");
                actions.put("Cancel deal", "CANCELLED");
                actions.put("Open dispute", "DISPUTED");
                break;
            case "DELIVERED":
                if (me.equals(deal.buyerId)) actions.put("Complete deal", "COMPLETED");
                actions.put("Open dispute", "DISPUTED");
                break;
            case "DISPUTED":
                if (me.equals(deal.buyerId)) actions.put("Resolve as completed", "COMPLETED");
                actions.put("Close as cancelled", "CANCELLED");
                break;
            default:
                break;
        }
        return actions;
    }

    private void showActionPicker(DealApiClient.RemoteDeal deal, Map<String,String> actions) {
        String[] labels = actions.keySet().toArray(new String[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle("Deal action")
                .setItems(labels, (dialog, which) -> confirmRemoteAction(deal, labels[which], actions.get(labels[which])))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoteAction(DealApiClient.RemoteDeal deal, String label, String status) {
        new AlertDialog.Builder(requireContext())
                .setTitle(label + "?")
                .setMessage("This updates the shared deal record for both users.")
                .setNegativeButton("Back", null)
                .setPositiveButton("Confirm", (dialog, which) -> DealApiClient.transition(deal.id, status, new DealApiClient.DealCallback() {
                    @Override public void onSuccess(DealApiClient.RemoteDeal updated) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), "Deal updated: " + prettyStatus(updated.status), Toast.LENGTH_SHORT).show();
                        refresh();
                    }
                    @Override public void onFailure(String message) {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                    }
                }))
                .show();
    }

    private String prettyStatus(String status) {
        if (status == null) return "Unknown";
        return status.replace('_', ' ');
    }

    private void confirmDelete(DealDraft draft) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete draft?")
                .setMessage("This draft has not been sent to the other user yet.")
                .setNegativeButton("Keep", null)
                .setPositiveButton("Delete", (d, which) -> {
                    DealDraftStore.remove(requireContext(), draft.id);
                    refresh();
                })
                .show();
    }

    private class DealsAdapter extends RecyclerView.Adapter<DealsAdapter.DealViewHolder> {
        private final List<DealDraft> drafts = new ArrayList<>();
        private final List<DealApiClient.RemoteDeal> remotes = new ArrayList<>();

        void setItems(List<DealDraft> local, List<DealApiClient.RemoteDeal> remote) {
            drafts.clear(); drafts.addAll(local);
            remotes.clear(); remotes.addAll(remote);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public DealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new DealViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deal_card, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull DealViewHolder holder, int position) {
            if (position < drafts.size()) {
                DealDraft d = drafts.get(position);
                holder.title.setText(d.title);
                holder.counterpart.setText(displayCounterpart(d.counterpart) + " • " + ("BUYER".equals(d.role) ? "You buy" : "You sell"));
                holder.amount.setText(d.currency + " " + d.amount);
                holder.status.setText("Draft");
                holder.itemView.setOnClickListener(v -> showDraftDetails(d));
                holder.itemView.setOnLongClickListener(v -> { confirmDelete(d); return true; });
                return;
            }

            DealApiClient.RemoteDeal d = remotes.get(position - drafts.size());
            String me = Cache.getTinode().getMyId();
            holder.title.setText(d.title);
            holder.counterpart.setText(displayCounterpart(d.counterpartFor(me)) + " • " + (me != null && me.equals(d.buyerId) ? "You buy" : "You sell"));
            holder.amount.setText(d.currency + " " + d.amountDisplay());
            holder.status.setText(prettyStatus(d.status));
            holder.itemView.setOnClickListener(v -> showRemoteDetails(d));
            holder.itemView.setOnLongClickListener(null);
        }

        @Override public int getItemCount() { return drafts.size() + remotes.size(); }

        class DealViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView counterpart;
            final TextView amount;
            final Chip status;
            DealViewHolder(View v) {
                super(v);
                title = v.findViewById(R.id.dealTitle);
                counterpart = v.findViewById(R.id.dealCounterpart);
                amount = v.findViewById(R.id.dealAmount);
                status = v.findViewById(R.id.dealStatusChip);
            }
        }
    }
}