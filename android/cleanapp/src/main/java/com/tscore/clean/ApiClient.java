package com.tscore.clean;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ApiClient {
    interface Callback {
        void onSuccess(JSONObject body);
        void onError(String message);
    }

    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    void login(String login, String password, Callback callback) {
        try { JSONObject p=new JSONObject(); p.put("login",login); p.put("password",password); request("POST","/api/v1/auth/login",p,null,callback); }
        catch(Exception e){ callback.onError("Unable to prepare login request"); }
    }

    void signup(String username,String email,String fullName,String password,Callback callback){
        try{JSONObject p=new JSONObject();p.put("username",username);p.put("email",email);p.put("full_name",fullName);p.put("password",password);request("POST","/api/v1/auth/signup",p,null,callback);}catch(Exception e){callback.onError("Unable to prepare signup request");}
    }

    void me(String token,Callback callback){request("GET","/api/v1/me",null,token,callback);}
    void updateMe(String token,String fullName,Callback callback){try{JSONObject p=new JSONObject();p.put("full_name",fullName);request("PATCH","/api/v1/me",p,token,callback);}catch(Exception e){callback.onError("Unable to update profile");}}
    void logout(String token,Callback callback){request("POST","/api/v1/auth/logout",new JSONObject(),token,callback);}

    void people(String token,String query,Callback callback){String path="/api/v1/people";if(query!=null&&!query.trim().isEmpty())path+="?q="+Uri.encode(query.trim());request("GET",path,null,token,callback);}
    void person(String token,int personId,Callback callback){request("GET","/api/v1/people/"+personId,null,token,callback);}
    void reviews(String token,int personId,Callback callback){request("GET","/api/v1/people/"+personId+"/reviews",null,token,callback);}
    void block(String token,int personId,Callback callback){request("POST","/api/v1/people/"+personId+"/block",new JSONObject(),token,callback);}
    void report(String token,int personId,String reason,Callback callback){try{JSONObject p=new JSONObject();p.put("reason",reason);request("POST","/api/v1/people/"+personId+"/report",p,token,callback);}catch(Exception e){callback.onError("Unable to report user");}}

    void chats(String token,Callback callback){request("GET","/api/v1/chats",null,token,callback);}
    void messages(String token,int personId,Callback callback){request("GET","/api/v1/chats/"+personId+"/messages",null,token,callback);}
    void sendMessage(String token,int recipientId,String body,Callback callback){try{JSONObject p=new JSONObject();p.put("recipient_id",recipientId);p.put("body",body);request("POST","/api/v1/messages",p,token,callback);}catch(Exception e){callback.onError("Unable to prepare message");}}

    void deals(String token,Callback callback){request("GET","/api/v1/deals",null,token,callback);}
    void createDeal(String token,String title,double amount,Integer counterpartyId,Callback callback){try{JSONObject p=new JSONObject();p.put("title",title);p.put("amount",amount);if(counterpartyId!=null)p.put("counterparty_id",counterpartyId);request("POST","/api/v1/deals",p,token,callback);}catch(Exception e){callback.onError("Unable to prepare deal");}}
    void updateDealStatus(String token,int dealId,String status,Callback callback){try{JSONObject p=new JSONObject();p.put("status",status);request("PATCH","/api/v1/deals/"+dealId+"/status",p,token,callback);}catch(Exception e){callback.onError("Unable to update deal");}}
    void reviewDeal(String token,int dealId,int rating,String comment,Callback callback){try{JSONObject p=new JSONObject();p.put("rating",rating);p.put("comment",comment);request("POST","/api/v1/deals/"+dealId+"/review",p,token,callback);}catch(Exception e){callback.onError("Unable to submit review");}}

    private void request(String method,String path,JSONObject payload,String token,Callback callback){
        executor.execute(()->{HttpURLConnection conn=null;try{URL url=new URL(baseUrl+path);conn=(HttpURLConnection)url.openConnection();conn.setConnectTimeout(8000);conn.setReadTimeout(10000);conn.setRequestMethod(method);conn.setRequestProperty("Accept","application/json");if(token!=null&&!token.isEmpty())conn.setRequestProperty("Authorization","Bearer "+token);if(payload!=null){conn.setRequestProperty("Content-Type","application/json");conn.setDoOutput(true);byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=conn.getOutputStream()){out.write(bytes);}}int code=conn.getResponseCode();InputStream input=code>=200&&code<300?conn.getInputStream():conn.getErrorStream();String body=readAll(input);JSONObject json=body.isEmpty()?new JSONObject():new JSONObject(body);if(code>=200&&code<300){main.post(()->callback.onSuccess(json));}else{String detail=json.optString("detail","Request failed");main.post(()->callback.onError(detail));}}catch(Exception e){main.post(()->callback.onError("Server connection failed"));}finally{if(conn!=null)conn.disconnect();}});
    }

    private String readAll(InputStream input)throws Exception{if(input==null)return"";StringBuilder b=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)b.append(line);}return b.toString();}
}
