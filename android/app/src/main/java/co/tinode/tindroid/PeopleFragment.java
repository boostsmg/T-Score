package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PeopleFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle state) {
        View root = inflater.inflate(R.layout.fragment_people, container, false);

        Button find = root.findViewById(R.id.peopleStartChat);
        if (find != null) {
            find.setText("Find trusted people");
            find.setOnClickListener(v -> startActivity(new Intent(requireContext(), StartChatActivity.class)));
        }

        TextView emptyTitle = root.findViewById(R.id.emptyTitle);
        TextView emptySubtitle = root.findViewById(R.id.emptySubtitle);
        if (emptyTitle != null) emptyTitle.setText("Find trusted people");
        if (emptySubtitle != null) emptySubtitle.setText("Search by username, review a person's T-Score and verification badges, then start a conversation with confidence.");

        RecyclerView peopleList = root.findViewById(R.id.peopleList);
        View emptyState = root.findViewById(R.id.peopleEmptyState);
        if (peopleList != null) {
            peopleList.setLayoutManager(new LinearLayoutManager(requireContext()));
            PeopleAdapter adapter = new PeopleAdapter();
            peopleList.setAdapter(adapter);
            boolean empty = adapter.getItemCount() == 0;
            peopleList.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        return root;
    }

    private static class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.PersonViewHolder> {
        @NonNull @Override
        public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_person_card, parent, false);
            return new PersonViewHolder(v);
        }
        @Override public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) { }
        @Override public int getItemCount() { return 0; }
        static class PersonViewHolder extends RecyclerView.ViewHolder { PersonViewHolder(View v) { super(v); } }
    }
}
