package co.tinode.tindroid;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DealsFragment extends Fragment {
    private RecyclerView dealsList;
    private View emptyState;
    private Chip chipActive;
    private Chip chipCompleted;
    private Chip chipDisputed;
    private DealsAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_deals, container, false);

        TextView emptyTitle = root.findViewById(R.id.emptyTitle);
        TextView emptySubtitle = root.findViewById(R.id.emptySubtitle);
        if (emptyTitle != null) emptyTitle.setText("No deals yet");
        if (emptySubtitle != null) emptySubtitle.setText("Start a trusted deal. Drafts stay on this device until secure server sync is enabled.");

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
        if (start != null) start.setOnClickListener(v -> showCreateDealDialog());

        refresh();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) refresh();
    }

    private void refresh() {
        List<DealDraft> drafts = DealDraftStore.load(requireContext());
        if (adapter != null) adapter.setItems(drafts);
        boolean empty = drafts.isEmpty();
        if (dealsList != null) dealsList.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (chipActive != null) chipActive.setText(drafts.size() + " Draft");
        if (chipCompleted != null) chipCompleted.setText("0 Completed");
        if (chipDisputed != null) chipDisputed.setText("0 Disputed");
    }

    private void showCreateDealDialog() {
        View form = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_deal, null, false);
        EditText counterpart = form.findViewById(R.id.dealCounterpartInput);
        EditText title = form.findViewById(R.id.dealTitleInput);
        EditText amount = form.findViewById(R.id.dealAmountInput);
        EditText currency = form.findViewById(R.id.dealCurrencyInput);
        EditText terms = form.findViewById(R.id.dealTermsInput);
        RadioButton buyer = form.findViewById(R.id.dealRoleBuyer);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save Draft", null)
                .create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String other = cleanUsername(counterpart.getText().toString());
            String dealTitle = title.getText().toString().trim();
            String dealAmount = amount.getText().toString().trim();
            String dealCurrency = currency.getText().toString().trim().toUpperCase(Locale.US);
            String dealTerms = terms.getText().toString().trim();
            String role = buyer.isChecked() ? "BUYER" : "SELLER";

            if (TextUtils.isEmpty(other)) {
                counterpart.setError("Username required");
                return;
            }
            if (dealTitle.length() < 3) {
                title.setError("Add a clear deal title");
                return;
            }
            if (TextUtils.isEmpty(dealAmount)) {
                amount.setError("Amount required");
                return;
            }
            try {
                if (Double.parseDouble(dealAmount) <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                amount.setError("Enter a valid amount");
                return;
            }
            if (dealCurrency.length() != 3) {
                currency.setError("Use 3-letter currency, e.g. PKR");
                return;
            }

            DealDraftStore.add(requireContext(), DealDraft.create(other, role, dealTitle, dealAmount, dealCurrency, dealTerms));
            dialog.dismiss();
            refresh();
            Toast.makeText(requireContext(), "Deal draft saved", Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private String cleanUsername(String value) {
        String out = value == null ? "" : value.trim();
        while (out.startsWith("@")) out = out.substring(1);
        return out.toLowerCase(Locale.US);
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
        private final List<DealDraft> items = new ArrayList<>();

        void setItems(List<DealDraft> drafts) {
            items.clear();
            items.addAll(drafts);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public DealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deal_card, parent, false);
            return new DealViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull DealViewHolder holder, int position) {
            DealDraft d = items.get(position);
            holder.title.setText(d.title);
            holder.counterpart.setText("@" + d.counterpart + " • " + ("BUYER".equals(d.role) ? "You buy" : "You sell"));
            holder.amount.setText(d.currency + " " + d.amount);
            holder.status.setText("Draft");
            holder.itemView.setOnLongClickListener(v -> {
                confirmDelete(d);
                return true;
            });
        }

        @Override public int getItemCount() { return items.size(); }

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
