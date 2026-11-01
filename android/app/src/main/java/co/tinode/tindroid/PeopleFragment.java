package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class PeopleFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);

        TextView title = new TextView(requireContext());
        title.setText("People");
        title.setTextSize(28);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView sub = new TextView(requireContext());
        sub.setText("Find people, check their profile and Trust Score before you chat or deal.");
        sub.setTextSize(17);
        sub.setPadding(0, 24, 0, 40);
        root.addView(sub);

        Button find = new Button(requireContext());
        find.setText("Find / Start Chat");
        find.setOnClickListener(v -> startActivity(new Intent(requireContext(), StartChatActivity.class)));
        root.addView(find);
        return root;
    }
}
