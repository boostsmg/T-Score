package co.tinode.tindroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only client representation of the T-Score trust breakdown.
 * The authoritative score is calculated by the server; this class only
 * defines labels/maximum points for consistent rendering.
 */
public final class TrustScoreModel {
    public static final class Item {
        public final String title;
        public final int maxPoints;

        Item(String title, int maxPoints) {
            this.title = title;
            this.maxPoints = maxPoints;
        }
    }

    private static final List<Item> ITEMS;

    static {
        List<Item> items = new ArrayList<>();
        items.add(new Item("Profile Photo", 5));
        items.add(new Item("Email", 5));
        items.add(new Item("Phone number", 10));
        items.add(new Item("CNIC / National ID", 20));
        items.add(new Item("Address", 15));
        items.add(new Item("Selfie / liveness", 10));
        items.add(new Item("Account age", 5));
        items.add(new Item("Successful deals", 15));
        items.add(new Item("Reviews", 10));
        items.add(new Item("Account standing", 5));
        ITEMS = Collections.unmodifiableList(items);
    }

    private TrustScoreModel() { }

    public static List<Item> items() {
        return ITEMS;
    }

    public static int maximumScore() {
        int total = 0;
        for (Item item : ITEMS) total += item.maxPoints;
        return total;
    }
}
