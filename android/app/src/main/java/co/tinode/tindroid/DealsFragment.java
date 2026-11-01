package co.tinode.tindroid;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

public class DealsFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_deals, container, false);

        // Wire empty state placeholders
        TextView emptyTitle = root.findViewById(R.id.emptyTitle);
        TextView emptySubtitle = root.findViewById(R.id.emptySubtitle);
        if (emptyTitle != null) emptyTitle.setText(R.string.no_deals_title);
        if (emptySubtitle != null) emptySubtitle.setText(R.string.no_deals_subtitle);

        // Status chips
        Chip chipActive = root.findViewById(R.id.dealChipActive);
        Chip chipCompleted = root.findViewById(R.id.dealChipCompleted);
        Chip chipDisputed = root.findViewById(R.id.dealChipDisputed);
        if (chipActive != null) chipActive.setText("Active");
        if (chipCompleted != null) chipCompleted.setText("Completed");
        if (chipDisputed != null) chipDisputed.setText("Disputed");

        // RecyclerView setup for deal items
        RecyclerView dealsList = root.findViewById(R.id.dealsList);
        if (dealsList != null) {
            dealsList.setLayoutManager(new LinearLayoutManager(requireContext()));
            // Adapter will be populated when backend deal data is available
            dealsList.setAdapter(new DealsAdapter(requireContext()));
        }

        return root;
    }

    /**
     * Placeholder adapter for deal items. Backend data integration is preserved
     * and can be wired here without changing layout structure.
     */
    private static class DealsAdapter extends RecyclerView.Adapter<DealsAdapter.DealViewHolder> {
        DealsAdapter(android.content.Context ctx) { /* no-op: data comes from backend */ }
        @NonNull @Override
        public DealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_deal_card, parent, false);
            return new DealViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull DealViewHolder holder, int position) { /* no-op */ }
        @Override
        public int getItemCount() { return 0; }
        static class DealViewHolder extends RecyclerView.ViewHolder {
            DealViewHolder(View v) { super(v); }
        }
    }
}
