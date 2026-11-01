package co.tinode.tindroid;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DealDraftStore {
    private static final String PREFS = "tscore_deal_drafts";
    private static final String KEY = "drafts";

    private DealDraftStore() { }

    public static List<DealDraft> load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY, "[]");
        List<DealDraft> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(DealDraft.fromJson(o));
            }
        } catch (Exception ignored) { }
        Collections.sort(out, Comparator.comparingLong((DealDraft d) -> d.createdAt).reversed());
        return out;
    }

    public static void add(Context context, DealDraft draft) {
        List<DealDraft> drafts = load(context);
        drafts.add(0, draft);
        save(context, drafts);
    }

    public static void remove(Context context, String id) {
        List<DealDraft> drafts = load(context);
        drafts.removeIf(d -> d.id.equals(id));
        save(context, drafts);
    }

    private static void save(Context context, List<DealDraft> drafts) {
        JSONArray arr = new JSONArray();
        try {
            for (DealDraft d : drafts) arr.put(d.toJson());
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}
