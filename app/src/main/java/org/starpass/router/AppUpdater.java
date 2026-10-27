package org.starpass.router;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppUpdater {
    private final Activity activity;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean closed,cancelled;
    private volatile HttpURLConnection connection;
    private boolean busy,awaitingPermission;
    private AlertDialog dialog;
    AppUpdater(Activity activity,Bundle state){this.activity=activity;awaitingPermission=state!=null&&state.getBoolean("updatePermission");}
    private PackageInfo installed() throws Exception{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),flags());}
    private int flags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    String currentVersion(){try{return installed().versionName;}catch(Exception e){return "1.1.0";}}
    private void ui(Runnable action){activity.runOnUiThread(()->{if(!closed&&!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void error(String text){dismiss();dialog=new AlertDialog.Builder(activity).setTitle("应用更新").setMessage(text).setPositiveButton("确定",null).show();}
    private void progress(String text){dismiss();dialog=new AlertDialog.Builder(activity).setTitle("应用更新").setMessage(text).setNegativeButton("取消",(d,w)->{cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}).setCancelable(false).show();}
    void check(){
        if(busy)return;busy=true;cancelled=false;progress("正在检查 GitHub 最新正式版本…");
        worker.execute(()->{
            try{
                Release release=latest();
                ui(()->{busy=false;dismiss();if(cancelled)return;
                    if(release==null||!UpdatePolicy.newer(release.version,currentVersion())){error("当前版本 v"+currentVersion()+"，已是最新正式版。");return;}
                    dialog=new AlertDialog.Builder(activity).setTitle("发现新版本 v"+release.version)
                        .setMessage("当前版本：v"+currentVersion()+"\n大小："+String.format(java.util.Locale.ROOT,"%.1f",release.size/1048576.0)+" MB\n\n"+release.notes+"\n\n下载后校验文件和签名，再由系统确认安装。")
                        .setNegativeButton("稍后",null).setPositiveButton("下载并安装",(d,w)->download(release)).show();
                });
            }catch(Exception e){ui(()->{busy=false;if(!cancelled)error("检查失败："+e.getMessage()+"\n请确认手机能访问 GitHub，稍后重试。");});}
        });
    }
    private HttpURLConnection open(String url,boolean api) throws Exception {
        for(int redirects=0;redirects<=5;redirects++){
            if(closed||cancelled)throw new Exception("已取消");
            if(api?!UpdatePolicy.LATEST.equals(url):!UpdatePolicy.downloadHost(url))throw new Exception("更新地址不受信任");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();connection=c;
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent","StarPass-Router/"+currentVersion());
            c.setRequestProperty("Accept",api?"application/vnd.github+json":"application/octet-stream");
            int status=c.getResponseCode();
            if(!api&&(status==301||status==302||status==303||status==307||status==308)){
                String next=c.getHeaderField("Location");c.disconnect();if(next==null)throw new Exception("下载重定向无效");url=new URL(new URL(url),next).toString();continue;
            }
            if(api&&status==404){c.disconnect();return null;}
            if(status!=200){c.disconnect();throw new Exception("HTTP "+status);}
            return c;
        }
        throw new Exception("下载重定向过多");
    }
    static final class Release {
        final String version,url,hash,notes;final long size;
        Release(String version,String url,String hash,long size,String notes){this.version=version;this.url=url;this.hash=hash;this.size=size;this.notes=notes;}
    }
    static Release parseRelease(JSONObject json,String current) throws Exception {
        if(json.optBoolean("draft")||json.optBoolean("prerelease"))return null;
        String version=UpdatePolicy.version(json.getString("tag_name"));
        if(!UpdatePolicy.newer(version,current))return null;
        JSONArray assets=json.getJSONArray("assets");
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.getJSONObject(i);
            if(!("StarPass-Router-"+version+".apk").equals(asset.optString("name")))continue;
            String url=asset.getString("browser_download_url");long size=asset.getLong("size");
            if(!UpdatePolicy.releaseAsset(url,version)||size<=0||size>UpdatePolicy.MAX_APK_BYTES)throw new Exception("发布文件地址或大小无效");
            String notes=json.optString("body","");if(notes.length()>4000)notes=notes.substring(0,4000)+"…";
            return new Release(version,url,UpdatePolicy.sha256(asset.optString("digest")),size,notes);
        }
        throw new Exception("新版本尚未上传正式 APK");
    }
    private Release latest() throws Exception {
        HttpURLConnection c=open(UpdatePolicy.LATEST,true);if(c==null)return null;
        try(InputStream in=c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1){if(closed||cancelled)throw new Exception("已取消");if(bytes.size()+count>1048576)throw new Exception("版本信息过大");bytes.write(buffer,0,count);}
            return parseRelease(new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())),currentVersion());
        }finally{c.disconnect();connection=null;}
    }
    private void download(Release release){
        if(busy)return;busy=true;cancelled=false;progress("正在下载 0%…");
        worker.execute(()->{
            File apk=UpdateProvider.apk(activity),partial=new File(apk.getParentFile(),"update.part");
            try{
                if(!apk.getParentFile().isDirectory()&&!apk.getParentFile().mkdirs())throw new Exception("无法创建更新目录");
                HttpURLConnection c=open(release.url,false);
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                    long size=0;int count,last=-1;byte[] buffer=new byte[65536];MessageDigest digest=MessageDigest.getInstance("SHA-256");
                    while((count=in.read(buffer))!=-1){
                        if(closed||cancelled||Thread.currentThread().isInterrupted())throw new Exception("已取消");
                        size+=count;if(size>release.size)throw new Exception("下载大小不符");out.write(buffer,0,count);digest.update(buffer,0,count);
                        int percent=(int)(size*100/release.size);if(percent!=last){last=percent;ui(()->{if(dialog!=null&&!cancelled)dialog.setMessage("正在下载 "+percent+"%…");});}
                    }
                    out.getFD().sync();
                    if(size!=release.size||!hex(digest.digest()).equals(release.hash))throw new Exception("下载校验失败，请重试");
                }finally{c.disconnect();connection=null;}
                verify(partial,release.version);
                if(closed||cancelled)throw new Exception("已取消");
                java.nio.file.Files.move(partial.toPath(),apk.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                ui(()->{busy=false;dismiss();if(!cancelled)install();});
            }catch(Exception e){partial.delete();ui(()->{busy=false;if(!cancelled)error("更新未完成："+e.getMessage());});}
        });
    }
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private long versionCode(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    private Set<String> signers(PackageInfo info) throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        if(signatures==null||signatures.length==0)throw new Exception("无法读取 APK 签名");
        Set<String> values=new HashSet<>();for(Signature signature:signatures)values.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return values;
    }
    void verify(File file,String expectedVersion) throws Exception {
        PackageInfo next=activity.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),flags()),current=installed();
        if(next==null||!activity.getPackageName().equals(next.packageName))throw new Exception("APK 包名不匹配");
        if(versionCode(next)<=versionCode(current)||!UpdatePolicy.newer(next.versionName,current.versionName)||(expectedVersion!=null&&!expectedVersion.equals(next.versionName)))throw new Exception("APK 版本不匹配或不是更新版本");
        if(!signers(next).equals(signers(current)))throw new Exception("APK 签名与当前应用不同，无法覆盖安装");
    }
    private void install(){
        try{
            File apk=UpdateProvider.apk(activity);verify(apk,null);
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                dialog=new AlertDialog.Builder(activity).setTitle("允许安装更新")
                    .setMessage("请在系统设置中允许星渡路由安装应用，返回后继续安装已验证的更新。")
                    .setNegativeButton("稍后",null).setPositiveButton("去设置",(d,w)->{
                        try{awaitingPermission=true;activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
                        catch(Exception e){awaitingPermission=false;error("无法打开安装权限设置，请在系统设置中允许本应用安装更新。");}
                    }).show();return;
            }
            Uri uri=UpdateProvider.uri(activity);
            Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("StarPass update",uri));activity.startActivity(intent);
        }catch(Exception e){error("无法安装更新："+e.getMessage());}
    }
    void saveState(Bundle state){state.putBoolean("updatePermission",awaitingPermission);}
    void onResume(){if(awaitingPermission){awaitingPermission=false;if(activity.getPackageManager().canRequestPackageInstalls())install();else error("尚未允许安装应用。可再次检查更新后重试。");}}
    void close(){closed=true;cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();worker.shutdownNow();dismiss();}
}
