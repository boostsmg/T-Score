package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PeopleFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_people, container, false);

        // Search field hint already in XML; keep existing button action
        Button find = root.findViewById(R.id.peopleStartChat);
        if (find != null) {
            find.setOnClickListener(v -> startActivity(new Intent(requireContext(), StartChatActivity.class)));
        }

        // RecyclerView setup for people cards
        RecyclerView peopleList = root.findViewById(R.id.peopleList);
        if (peopleList != null) {
            peopleList.setLayoutManager(new LinearLayoutManager(requireContext()));
            peopleList.setAdapter(new PeopleAdapter(requireContext()));
        }

        return root;
    }

    /**
     * Placeholder adapter for people cards. Backend contact/people data is preserved
     * and can be wired here without changing layout structure.
     */
    private static class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.PersonViewHolder> {
        PeopleAdapter(android.content.Context ctx) { /* no-op */ }
        @NonNull @Override
        public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_person_card, parent, false);
            return new PersonViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) { /* no-op */ }
        @Override
        public int getItemCount() { return 0; }
        static class PersonViewHolder extends RecyclerView.ViewHolder {
            PersonViewHolder(View v) { super(v); }
        }
    }
}
