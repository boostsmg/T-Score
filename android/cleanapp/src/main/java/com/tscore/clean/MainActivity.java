package com.tscore.clean;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private LinearLayout root;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showAuthScreen();
    }

    private void showAuthScreen() {
        root = vertical();
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = text("T-Score", 32, true);
        TextView subtitle = text("Deal with Confidence", 16, false);
        subtitle.setTextColor(Color.DKGRAY);

        EditText username = field("Username or email");
        EditText password = field("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button login = button("Sign In");
        Button create = button("Create Account");
        Button uiTest = button("Open App Preview");

        login.setOnClickListener(v -> Toast.makeText(this,
                "Clean backend authentication is being connected next.", Toast.LENGTH_SHORT).show());
        create.setOnClickListener(v -> Toast.makeText(this,
                "Clean account API is being connected next.", Toast.LENGTH_SHORT).show());
        uiTest.setOnClickListener(v -> showMainShell("Chats"));

        root.addView(title, matchWrap());
        root.addView(subtitle, matchWrap());
        addGap(root, 32);
        root.addView(username, matchWrap());
        addGap(root, 12);
        root.addView(password, matchWrap());
        addGap(root, 18);
        root.addView(login, matchWrap());
        addGap(root, 10);
        root.addView(create, matchWrap());
        addGap(root, 24);
        root.addView(uiTest, matchWrap());

        setContentView(root);
    }

    private void showMainShell(String selected) {
        root = vertical();
        root.setBackgroundColor(Color.WHITE);

        TextView top = text("T-Score", 24, true);
        top.setPadding(dp(20), dp(20), dp(20), dp(16));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        content = vertical();
        content.setGravity(Gravity.CENTER);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(8), dp(8), dp(12));
        nav.setBackgroundColor(Color.rgb(248, 250, 252));

        addTab(nav, "Chats");
        addTab(nav, "Deals");
        addTab(nav, "People");
        addTab(nav, "Profile");
        root.addView(nav, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        renderTab(selected);
    }

    private void addTab(LinearLayout nav, String name) {
        Button tab = new Button(this);
        tab.setText(name);
        tab.setAllCaps(false);
        tab.setOnClickListener(v -> renderTab(name));
        nav.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1));
    }

    private void renderTab(String name) {
        if (content == null) return;
        content.removeAllViews();

        TextView title = text(name, 28, true);
        TextView body;
        switch (name) {
            case "Deals":
                body = text("Create, track and safely complete deals here.", 16, false);
                break;
            case "People":
                body = text("Find people and check their Trust Score here.", 16, false);
                break;
            case "Profile":
                body = text("Your Trust Score, reviews and account settings live here.", 16, false);
                break;
            default:
                body = text("Your direct conversations will appear here.", 16, false);
                break;
        }
        body.setTextColor(Color.DKGRAY);
        body.setGravity(Gravity.CENTER);
        body.setPadding(dp(28), dp(12), dp(28), 0);
        content.addView(title, matchWrap());
        content.addView(body, matchWrap());
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(17, 24, 39));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
