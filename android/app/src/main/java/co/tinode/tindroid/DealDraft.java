package co.tinode.tindroid;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public final class DealDraft {
    public final String id;
    public final String counterpart;
    public final String role;
    public final String title;
    public final String amount;
    public final String currency;
    public final String terms;
    public final long createdAt;

    public DealDraft(String id, String counterpart, String role, String title,
                     String amount, String currency, String terms, long createdAt) {
        this.id = id;
        this.counterpart = counterpart;
        this.role = role;
        this.title = title;
        this.amount = amount;
        this.currency = currency;
        this.terms = terms;
        this.createdAt = createdAt;
    }

    public static DealDraft create(String counterpart, String role, String title,
                                   String amount, String currency, String terms) {
        return new DealDraft("draft_" + UUID.randomUUID(), counterpart, role, title,
                amount, currency, terms, System.currentTimeMillis());
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("counterpart", counterpart);
        o.put("role", role);
        o.put("title", title);
        o.put("amount", amount);
        o.put("currency", currency);
        o.put("terms", terms);
        o.put("createdAt", createdAt);
        return o;
    }

    static DealDraft fromJson(JSONObject o) {
        return new DealDraft(
                o.optString("id"), o.optString("counterpart"), o.optString("role"),
                o.optString("title"), o.optString("amount"), o.optString("currency", "PKR"),
                o.optString("terms"), o.optLong("createdAt", System.currentTimeMillis()));
    }
}
