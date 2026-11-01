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

        TextView emptyTitle = root.findViewById(R.id.emptyTitle);
        TextView emptySubtitle = root.findViewById(R.id.emptySubtitle);
        if (emptyTitle != null) emptyTitle.setText("No deals yet");
        if (emptySubtitle != null) emptySubtitle.setText("Start a trusted deal from a chat. Completed deals and honest reviews will build your T-Score over time.");

        Chip chipActive = root.findViewById(R.id.dealChipActive);
        Chip chipCompleted = root.findViewById(R.id.dealChipCompleted);
        Chip chipDisputed = root.findViewById(R.id.dealChipDisputed);
        if (chipActive != null) chipActive.setText("0 Active");
        if (chipCompleted != null) chipCompleted.setText("0 Completed");
        if (chipDisputed != null) chipDisputed.setText("0 Disputed");

        RecyclerView dealsList = root.findViewById(R.id.dealsList);
        View emptyState = root.findViewById(R.id.dealsEmptyState);
        if (dealsList != null) {
            dealsList.setLayoutManager(new LinearLayoutManager(requireContext()));
            DealsAdapter adapter = new DealsAdapter();
            dealsList.setAdapter(adapter);
            boolean empty = adapter.getItemCount() == 0;
            dealsList.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        return root;
    }

    private static class DealsAdapter extends RecyclerView.Adapter<DealsAdapter.DealViewHolder> {
        @NonNull @Override
        public DealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deal_card, parent, false);
            return new DealViewHolder(v);
        }
        @Override public void onBindViewHolder(@NonNull DealViewHolder holder, int position) { }
        @Override public int getItemCount() { return 0; }
        static class DealViewHolder extends RecyclerView.ViewHolder { DealViewHolder(View v) { super(v); } }
    }
}
