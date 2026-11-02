package co.tinode.tindroid;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class VerificationApiClient {
    private static final String BASE = "https://chat.socialrise.garden/tscore-api/v1/verifications";
    private static final long MAX_EVIDENCE = 8L * 1024L * 1024L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static final class Item {
        final String id,type,status,note;
        Item(JSONObject o){id=o.optString("id","");type=o.optString("type","");status=o.optString("status","");note=o.optString("note","");}
    }
    interface ListCallback { void onSuccess(List<Item> items); void onFailure(String message); }
    interface ActionCallback { void onSuccess(boolean evidenceRequired); void onFailure(String message); }
    interface UploadCallback { void onSuccess(); void onFailure(String message); }
    private VerificationApiClient(){}

    static void list(ListCallback cb){String t=token();if(t==null){cb.onFailure("Login session unavailable");return;}EXECUTOR.execute(()->{HttpURLConnection c=null;try{c=open(BASE,"GET",t);int code=c.getResponseCode();String body=read(c,code);if(code>=200&&code<300){JSONArray a=new JSONObject(body).optJSONArray("verifications");List<Item> out=new ArrayList<>();if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(new Item(o));}MAIN.post(()->cb.onSuccess(out));}else{String m=error(body,"Verifications unavailable");MAIN.post(()->cb.onFailure(m));}}catch(Exception e){MAIN.post(()->cb.onFailure("Verification server unavailable"));}finally{if(c!=null)c.disconnect();}});}

    static void create(String type,ActionCallback cb){String t=token();if(t==null){cb.onFailure("Login session unavailable");return;}EXECUTOR.execute(()->{HttpURLConnection c=null;try{JSONObject body=new JSONObject().put("type",type);c=open(BASE,"POST",t);byte[] p=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(p.length);try(OutputStream out=c.getOutputStream()){out.write(p);}int code=c.getResponseCode();String response=read(c,code);if(code>=200&&code<300){boolean evidence=new JSONObject(response).optBoolean("evidence_required",false);MAIN.post(()->cb.onSuccess(evidence));}else{String m=error(response,"Could not start verification");MAIN.post(()->cb.onFailure(m));}}catch(Exception e){MAIN.post(()->cb.onFailure("Verification server unavailable"));}finally{if(c!=null)c.disconnect();}});}

    static void uploadEvidence(Context context,String requestId,Uri uri,UploadCallback cb){
        if(context==null||uri==null||requestId==null||requestId.trim().isEmpty()){cb.onFailure("Invalid evidence file");return;}
        String t=token();if(t==null){cb.onFailure("Login session unavailable");return;}
        final String mime=context.getContentResolver().getType(uri);
        final long size=querySize(context,uri);
        if(size<=0||size>MAX_EVIDENCE){cb.onFailure("Evidence must be smaller than 8 MB");return;}
        if(!allowedMime(mime)){cb.onFailure("Use JPG, PNG, WebP or PDF");return;}
        EXECUTOR.execute(()->{HttpURLConnection c=null;try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IllegalStateException("Cannot open file");
            c=(HttpURLConnection)new URL(BASE+"/"+requestId+"/evidence").openConnection();
            c.setRequestMethod("PUT");c.setConnectTimeout(10000);c.setReadTimeout(20000);c.setDoOutput(true);
            c.setRequestProperty("Authorization","Bearer "+t);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Content-Type",mime);c.setFixedLengthStreamingMode(size);
            try(OutputStream out=c.getOutputStream()){byte[] b=new byte[32*1024];int n;long sent=0;while((n=in.read(b))!=-1){sent+=n;if(sent>MAX_EVIDENCE)throw new IllegalStateException("File too large");out.write(b,0,n);}}
            int code=c.getResponseCode();String body=read(c,code);if(code>=200&&code<300){MAIN.post(cb::onSuccess);}else{String m=error(body,"Evidence upload failed");MAIN.post(()->cb.onFailure(m));}
        }catch(Exception e){MAIN.post(()->cb.onFailure("Could not upload private evidence"));}finally{if(c!=null)c.disconnect();}});
    }

    private static boolean allowedMime(String mime){return "image/jpeg".equals(mime)||"image/png".equals(mime)||"image/webp".equals(mime)||"application/pdf".equals(mime);}
    private static long querySize(Context c,Uri uri){try(Cursor cur=c.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){if(cur!=null&&cur.moveToFirst()){int i=cur.getColumnIndex(OpenableColumns.SIZE);if(i>=0&&!cur.isNull(i))return cur.getLong(i);}}catch(Exception ignored){}return -1;}
    private static String token(){String t=Cache.getTinode().getAuthToken();return t==null||t.trim().isEmpty()?null:t;}
    private static HttpURLConnection open(String url,String method,String token)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setRequestMethod(method);c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setRequestProperty("Authorization","Bearer "+token);c.setRequestProperty("Accept","application/json");if("POST".equals(method)){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");}return c;}
    private static String read(HttpURLConnection c,int code)throws Exception{return all(code>=200&&code<300?c.getInputStream():c.getErrorStream());}
    private static String all(InputStream in)throws Exception{if(in==null)return "";StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}return s.toString();}
    private static String error(String body,String fallback){try{String e=new JSONObject(body).optString("error","");return e.isEmpty()?fallback:e;}catch(Exception ignored){return fallback;}}
}
