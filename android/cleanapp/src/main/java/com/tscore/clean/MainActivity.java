package com.tscore.clean;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String PREFS = "tscore_clean";

    private LinearLayout root;
    private LinearLayout content;
    private ApiClient api;
    private String sessionToken = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        api = new ApiClient(BuildConfig.API_BASE_URL);
        restoreSessionOrShowLogin();
    }

    private void restoreSessionOrShowLogin() {
        sessionToken = getSharedPreferences(PREFS, MODE_PRIVATE).getString("token", "");
        if (sessionToken == null || sessionToken.isEmpty()) {
            showAuthScreen();
            return;
        }
        showLoading("Checking your session...");
        api.me(sessionToken, new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject body) { saveUser(body); showMainShell("Chats"); }
            @Override public void onError(String message) { clearSession(); showAuthScreen(); }
        });
    }

    private void showLoading(String message) {
        root = vertical();
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.addView(text(message, 18, true), matchWrap());
        setContentView(root);
    }

    private void showAuthScreen() {
        root = vertical();
        root.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);
        root.addView(text("T-Score", 32, true), matchWrap());
        TextView subtitle = text("Deal with Confidence", 16, false);
        subtitle.setTextColor(Color.DKGRAY);
        root.addView(subtitle, matchWrap());
        addGap(root, 32);

        EditText username = field("Username or email");
        EditText password = field("Password");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button login = button("Sign In");
        Button create = button("Create Account");

        login.setOnClickListener(v -> {
            String loginValue = username.getText().toString().trim();
            String passwordValue = password.getText().toString();
            if (loginValue.isEmpty() || passwordValue.length() < 8) { toast("Username/email aur 8+ character password chahiy"); return; }
            login.setEnabled(false);
            api.login(loginValue, passwordValue, new ApiClient.Callback() {
                @Override public void onSuccess(JSONObject body) { login.setEnabled(true); saveSession(body); showMainShell("Chats"); }
                @Override public void onError(String message) { login.setEnabled(true); toast(message); }
            });
        });
        create.setOnClickListener(v -> showSignupScreen());

        root.addView(username, matchWrap()); addGap(root, 12);
        root.addView(password, matchWrap()); addGap(root, 18);
        root.addView(login, matchWrap()); addGap(root, 10);
        root.addView(create, matchWrap());
        setContentView(root);
    }

    private void showSignupScreen() {
        root = vertical();
        root.setPadding(dp(24), dp(30), dp(24), dp(24));
        root.setBackgroundColor(Color.WHITE);
        root.addView(text("Create Account", 28, true), matchWrap()); addGap(root, 24);
        EditText username = field("Username");
        EditText email = field("Email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText fullName = field("Full name");
        EditText password = field("Password (8+ characters)");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button create = button("Create Account");
        Button back = button("Back to Sign In");
        create.setOnClickListener(v -> {
            String u=username.getText().toString().trim(), e=email.getText().toString().trim(), n=fullName.getText().toString().trim(), p=password.getText().toString();
            if (u.length()<3 || !e.contains("@") || n.length()<2 || p.length()<8) { toast("Please sary fields sahi fill kro"); return; }
            create.setEnabled(false);
            api.signup(u,e,n,p,new ApiClient.Callback(){
                @Override public void onSuccess(JSONObject body){ create.setEnabled(true); saveSession(body); showMainShell("Chats"); }
                @Override public void onError(String message){ create.setEnabled(true); toast(message); }
            });
        });
        back.setOnClickListener(v -> showAuthScreen());
        root.addView(username, matchWrap()); addGap(root,10); root.addView(email, matchWrap()); addGap(root,10);
        root.addView(fullName, matchWrap()); addGap(root,10); root.addView(password, matchWrap()); addGap(root,18);
        root.addView(create, matchWrap()); addGap(root,10); root.addView(back, matchWrap());
        setContentView(root);
    }

    private void saveSession(JSONObject body) {
        sessionToken = body.optString("token", "");
        JSONObject user = body.optJSONObject("user");
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("token", sessionToken);
        if (user != null) saveUserToEditor(user, editor);
        editor.apply();
    }

    private void saveUser(JSONObject user) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        saveUserToEditor(user, editor); editor.apply();
    }

    private void saveUserToEditor(JSONObject user, SharedPreferences.Editor editor) {
        editor.putInt("user_id", user.optInt("id", 0));
        editor.putString("username", user.optString("username", ""));
        editor.putString("full_name", user.optString("full_name", ""));
        editor.putString("email", user.optString("email", ""));
        editor.putInt("trust_score", user.optInt("trust_score", 50));
    }

    private void clearSession() { sessionToken=""; getSharedPreferences(PREFS,MODE_PRIVATE).edit().clear().apply(); }

    private void showMainShell(String selected) {
        root = vertical(); root.setBackgroundColor(Color.WHITE);
        TextView top = text("T-Score",24,true); top.setPadding(dp(20),dp(20),dp(20),dp(16)); root.addView(top,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll = new ScrollView(this);
        content = vertical(); content.setPadding(dp(18),dp(12),dp(18),dp(18));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setGravity(Gravity.CENTER); nav.setPadding(dp(6),dp(6),dp(6),dp(10)); nav.setBackgroundColor(Color.rgb(248,250,252));
        addTab(nav,"Chats"); addTab(nav,"Deals"); addTab(nav,"People"); addTab(nav,"Profile"); root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root); renderTab(selected);
    }

    private void addTab(LinearLayout nav,String name){ Button tab=button(name); tab.setOnClickListener(v->renderTab(name)); nav.addView(tab,new LinearLayout.LayoutParams(0,dp(48),1)); }

    private void renderTab(String name){
        if(content==null)return; content.removeAllViews(); content.setGravity(Gravity.NO_GRAVITY); content.setPadding(dp(18),dp(12),dp(18),dp(18));
        switch(name){ case "People": renderPeople(""); break; case "Chats": renderChats(); break; case "Deals": renderDeals(); break; case "Profile": renderProfile(); break; }
    }

    private void renderPeople(String initialQuery){
        content.removeAllViews(); content.addView(text("People",28,true),matchWrap()); addGap(content,10);
        EditText search=field("Search username or name"); search.setText(initialQuery); Button searchBtn=button("Search");
        content.addView(search,matchWrap()); addGap(content,8); content.addView(searchBtn,matchWrap()); addGap(content,16); LinearLayout list=vertical(); content.addView(list,matchWrap());
        View.OnClickListener runSearch=v->{ list.removeAllViews(); list.addView(text("Loading...",15,false),matchWrap()); api.people(sessionToken,search.getText().toString(),new ApiClient.Callback(){
            @Override public void onSuccess(JSONObject body){ list.removeAllViews(); JSONArray items=body.optJSONArray("items"); if(items==null||items.length()==0){list.addView(text("No users found",15,false),matchWrap());return;} for(int i=0;i<items.length();i++){ JSONObject p=items.optJSONObject(i); if(p==null)continue; int id=p.optInt("id"); Button row=button(p.optString("full_name")+"   @"+p.optString("username")+"   • "+p.optInt("trust_score",50)); row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); row.setOnClickListener(x->showPersonProfile(id)); list.addView(row,matchWrap()); addGap(list,6);} }
            @Override public void onError(String message){ list.removeAllViews(); list.addView(text(message,15,false),matchWrap()); }
        });}; searchBtn.setOnClickListener(runSearch); runSearch.onClick(searchBtn);
    }

    private void showPersonProfile(int personId){
        content.removeAllViews(); content.addView(text("Loading profile...",16,false),matchWrap()); api.person(sessionToken,personId,new ApiClient.Callback(){
            @Override public void onSuccess(JSONObject p){ content.removeAllViews(); Button back=button("← People"); back.setOnClickListener(v->renderPeople("")); content.addView(back,matchWrap()); addGap(content,12); content.addView(text(p.optString("full_name","User"),28,true),matchWrap()); content.addView(text("@"+p.optString("username",""),16,false),matchWrap()); addGap(content,14); content.addView(text("Trust Score: "+p.optInt("trust_score",50)+"/100",22,true),matchWrap()); addGap(content,22); Button msg=button("Message"); msg.setOnClickListener(v->showConversation(personId,p.optString("full_name","Chat"))); content.addView(msg,matchWrap()); addGap(content,8); Button deal=button("Create Deal"); deal.setOnClickListener(v->showCreateDeal(personId,p.optString("full_name","User"))); content.addView(deal,matchWrap()); }
            @Override public void onError(String message){ toast(message); renderPeople(""); }
        });
    }

    private void renderChats(){
        content.removeAllViews(); content.addView(text("Chats",28,true),matchWrap()); addGap(content,12); LinearLayout list=vertical(); content.addView(list,matchWrap()); list.addView(text("Loading...",15,false),matchWrap());
        api.chats(sessionToken,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject body){ list.removeAllViews(); JSONArray items=body.optJSONArray("items"); if(items==null||items.length()==0){ list.addView(text("No conversations yet. People tab se kisi ko message kro.",15,false),matchWrap()); return; } for(int i=0;i<items.length();i++){ JSONObject item=items.optJSONObject(i); if(item==null)continue; JSONObject p=item.optJSONObject("partner"); if(p==null)continue; int id=p.optInt("id"); String name=p.optString("full_name","User"), last=item.optString("last_message",""); Button row=button(name+"\n"+last); row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL); row.setOnClickListener(v->showConversation(id,name)); list.addView(row,matchWrap()); addGap(list,6); } }
            @Override public void onError(String message){ list.removeAllViews(); list.addView(text(message,15,false),matchWrap()); } });
    }

    private void showConversation(int personId,String name){
        content.removeAllViews(); Button back=button("← Chats"); back.setOnClickListener(v->renderChats()); content.addView(back,matchWrap()); addGap(content,10); content.addView(text(name,26,true),matchWrap()); addGap(content,12); LinearLayout messages=vertical(); content.addView(messages,matchWrap()); addGap(content,14); EditText box=field("Write a message"); Button send=button("Send"); content.addView(box,matchWrap()); addGap(content,6); content.addView(send,matchWrap());
        Runnable load=()->{ messages.removeAllViews(); messages.addView(text("Loading...",14,false),matchWrap()); api.messages(sessionToken,personId,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject body){ messages.removeAllViews(); JSONArray items=body.optJSONArray("items"); if(items==null||items.length()==0){messages.addView(text("No messages yet",14,false),matchWrap());return;} int myId=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("user_id",0); for(int i=0;i<items.length();i++){ JSONObject m=items.optJSONObject(i); if(m==null)continue; boolean mine=m.optInt("sender_id")==myId; TextView t=text((mine?"Me: ":name+": ")+m.optString("body"),15,mine); t.setPadding(dp(8),dp(7),dp(8),dp(7)); messages.addView(t,matchWrap()); }} @Override public void onError(String message){messages.removeAllViews();messages.addView(text(message,14,false),matchWrap());} });};
        send.setOnClickListener(v->{ String body=box.getText().toString().trim(); if(body.isEmpty())return; send.setEnabled(false); api.sendMessage(sessionToken,personId,body,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject response){box.setText("");send.setEnabled(true);load.run();} @Override public void onError(String message){send.setEnabled(true);toast(message);} });}); load.run();
    }

    private void renderDeals(){
        content.removeAllViews(); LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.addView(text("Deals",28,true),new LinearLayout.LayoutParams(0,-2,1)); Button create=button("+ New"); header.addView(create,new LinearLayout.LayoutParams(-2,-2)); content.addView(header,matchWrap()); addGap(content,12); create.setOnClickListener(v->showCreateDeal(null,null)); LinearLayout list=vertical(); content.addView(list,matchWrap()); list.addView(text("Loading...",15,false),matchWrap());
        api.deals(sessionToken,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject body){ list.removeAllViews(); JSONArray items=body.optJSONArray("items"); if(items==null||items.length()==0){list.addView(text("No deals yet",15,false),matchWrap());return;} for(int i=0;i<items.length();i++){JSONObject d=items.optJSONObject(i);if(d==null)continue; list.addView(dealCard(d),matchWrap()); addGap(list,8);} } @Override public void onError(String message){list.removeAllViews();list.addView(text(message,15,false),matchWrap());} });
    }

    private View dealCard(JSONObject d){
        LinearLayout card=vertical(); card.setPadding(dp(12),dp(10),dp(12),dp(10)); card.setBackgroundColor(Color.rgb(247,248,250)); card.addView(text(d.optString("title","Deal"),18,true),matchWrap()); card.addView(text("Amount: "+d.optDouble("amount",0)+"   Status: "+d.optString("status","PENDING"),14,false),matchWrap()); JSONObject p=d.optJSONObject("counterparty"); if(p!=null)card.addView(text("With: "+p.optString("full_name",p.optString("username","")),14,false),matchWrap()); LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); int id=d.optInt("id"); String status=d.optString("status","PENDING"); if("PENDING".equals(status)){ Button accept=button("Accept"); accept.setOnClickListener(v->setDealStatus(id,"ACCEPTED")); actions.addView(accept,new LinearLayout.LayoutParams(0,-2,1)); Button cancel=button("Cancel"); cancel.setOnClickListener(v->setDealStatus(id,"CANCELLED")); actions.addView(cancel,new LinearLayout.LayoutParams(0,-2,1)); } else if("ACCEPTED".equals(status)){ Button progress=button("Start"); progress.setOnClickListener(v->setDealStatus(id,"IN_PROGRESS")); actions.addView(progress,new LinearLayout.LayoutParams(0,-2,1)); } else if("IN_PROGRESS".equals(status)){ Button done=button("Complete"); done.setOnClickListener(v->setDealStatus(id,"COMPLETED")); actions.addView(done,new LinearLayout.LayoutParams(0,-2,1)); Button dispute=button("Dispute"); dispute.setOnClickListener(v->setDealStatus(id,"DISPUTED")); actions.addView(dispute,new LinearLayout.LayoutParams(0,-2,1)); } if(actions.getChildCount()>0){ addGap(card,8); card.addView(actions,matchWrap()); } return card;
    }

    private void setDealStatus(int dealId,String status){ api.updateDealStatus(sessionToken,dealId,status,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject body){renderDeals();} @Override public void onError(String message){toast(message);} }); }

    private void showCreateDeal(Integer personId,String personName){
        content.removeAllViews(); Button back=button("← Deals"); back.setOnClickListener(v->renderDeals()); content.addView(back,matchWrap()); addGap(content,10); content.addView(text("Create Deal",26,true),matchWrap()); if(personName!=null)content.addView(text("With: "+personName,15,false),matchWrap()); addGap(content,12); EditText title=field("Deal title"); EditText amount=field("Amount"); amount.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); Button submit=button("Create Deal"); content.addView(title,matchWrap()); addGap(content,8); content.addView(amount,matchWrap()); addGap(content,12); content.addView(submit,matchWrap()); submit.setOnClickListener(v->{ String t=title.getText().toString().trim(), a=amount.getText().toString().trim(); if(t.length()<2||a.isEmpty()){toast("Title aur amount enter kro");return;} double value; try{value=Double.parseDouble(a);}catch(Exception e){toast("Amount sahi enter kro");return;} submit.setEnabled(false); api.createDeal(sessionToken,t,value,personId,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject body){toast("Deal created");renderDeals();} @Override public void onError(String message){submit.setEnabled(true);toast(message);} }); });
    }

    private void renderProfile(){
        content.removeAllViews(); content.addView(text("Loading profile...",16,false),matchWrap()); api.me(sessionToken,new ApiClient.Callback(){ @Override public void onSuccess(JSONObject user){ saveUser(user); content.removeAllViews(); content.addView(text(user.optString("full_name","Profile"),28,true),matchWrap()); content.addView(text("@"+user.optString("username",""),16,false),matchWrap()); content.addView(text(user.optString("email",""),15,false),matchWrap()); addGap(content,20); content.addView(text("Trust Score: "+user.optInt("trust_score",50)+"/100",22,true),matchWrap()); addGap(content,24); Button logout=button("Log Out"); logout.setOnClickListener(v->api.logout(sessionToken,new ApiClient.Callback(){@Override public void onSuccess(JSONObject b){clearSession();showAuthScreen();}@Override public void onError(String m){clearSession();showAuthScreen();}})); content.addView(logout,matchWrap()); } @Override public void onError(String message){clearSession();showAuthScreen();} });
    }

    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private EditText field(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setPadding(dp(14),dp(12),dp(14),dp(12));return e;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(17,24,39));if(bold)t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);return t;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private void addGap(LinearLayout parent,int heightDp){View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(heightDp)));}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
