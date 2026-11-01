package co.tinode.tindroid;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class DealsFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(requireContext());
        title.setText("Deals");
        title.setTextSize(28);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = new TextView(requireContext());
        sub.setText("Deal with confidence\n\nCreate and track buyer/seller deals here. Completed deals, reviews and disputes will contribute to Trust Score.");
        sub.setTextSize(17);
        sub.setPadding(0, 24, 0, 24);
        root.addView(sub);

        TextView empty = new TextView(requireContext());
        empty.setText("No deals yet\n\nDeal creation and escrow-style status flow is the next T-Score module.");
        empty.setTextSize(16);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(24, 80, 24, 80);
        root.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }
}
