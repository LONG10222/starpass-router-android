package org.starpass.router;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Included only in the private test APK, never in the delivered APK. */
public final class TestRunner extends Instrumentation {
    private Bundle arguments;
    private MainActivity activity;
    private String site;
    private int checks=0;
    @Override public void onCreate(Bundle bundle){arguments=bundle;start();}
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void ui(Runnable action){runOnMainSync(action);}
    private MainActivity launch(){
        Intent intent=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return (MainActivity)startActivitySync(intent);
    }
    private View text(View view,String target){
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(target))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View match=text(group.getChildAt(i),target);if(match!=null)return match;}}
        return null;
    }
    private boolean visible(View v){return v!=null&&v.isShown();}
    private void awaitText(String content) throws Exception {
        for(int i=0;i<200;i++){
            AtomicReference<Boolean> found=new AtomicReference<>(false);ui(()->found.set(visible(text(activity.getWindow().getDecorView(),content))));
            if(found.get())return;Thread.sleep(100);
        }
        throw new AssertionError("Missing state: "+content);
    }
    private void awaitAuthenticated() throws Exception {
        for(int i=0;i<250;i++){
            AtomicReference<Boolean> ready=new AtomicReference<>(false);
            ui(()->{WebView web=activity.findViewById(MainActivity.WEB_ID);ready.set(web.isShown()&&web.getUrl()!=null&&web.getUrl().contains("/admin/test"));});
            if(ready.get()){Thread.sleep(300);return;}Thread.sleep(100);
        }
        throw new AssertionError("Authenticated page not reached");
    }
    private void screenshot(String name) throws Exception {
        Bitmap image=getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private void submit(String password){ui(()->{
        ((EditText)activity.findViewById(MainActivity.SITE_ID)).setText(site);
        ((EditText)activity.findViewById(MainActivity.USER_ID)).setText("root");
        ((EditText)activity.findViewById(MainActivity.PASSWORD_ID)).setText(password);
        activity.findViewById(MainActivity.LOGIN_ID).performClick();
    });}
    private void checkLayout(){ui(()->{
        View a=activity.findViewById(MainActivity.SITE_ID),b=activity.findViewById(MainActivity.USER_ID),c=activity.findViewById(MainActivity.PASSWORD_ID);
        check(a.getTop()<b.getTop()&&b.getTop()<c.getTop(),"field order");
        check(a.getLeft()==b.getLeft()&&b.getLeft()==c.getLeft(),"aligned fields");
        check(a.getWidth()==b.getWidth()&&b.getWidth()==c.getWidth(),"equal widths");
        int[] location=new int[2];a.getLocationOnScreen(location);check(location[0]>=0&&location[0]+a.getWidth()<=getTargetContext().getResources().getDisplayMetrics().widthPixels,"phone field bounds");
    });}
    private void menu(String item) throws Exception {
        // AlertDialog is a separate window; use UiAutomation's accessible tree to select its item.
        ui(()->text(activity.getWindow().getDecorView(),"菜单").performClick());Thread.sleep(250);
        android.view.accessibility.AccessibilityNodeInfo window=getUiAutomation().getRootInActiveWindow();
        java.util.List<android.view.accessibility.AccessibilityNodeInfo> matches=window.findAccessibilityNodeInfosByText(item);
        check(!matches.isEmpty(),"menu item "+item);
        android.view.accessibility.AccessibilityNodeInfo node=matches.get(0);
        while(node!=null&&!node.isClickable())node=node.getParent();
        check(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"select "+item);Thread.sleep(400);
    }
    private void checkFullscreen() throws Exception {
        Thread.sleep(700);ui(()->{
            WebView web=activity.findViewById(MainActivity.WEB_ID);int[] xy=new int[2];web.getLocationOnScreen(xy);
            android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
            check(xy[0]==0&&xy[1]==0,"fullscreen starts at display origin: "+xy[0]+","+xy[1]);
            check(web.getWidth()==metrics.widthPixels,"fullscreen display width");
            check(web.getHeight()==metrics.heightPixels,"fullscreen display height: "+web.getHeight()+"/"+metrics.heightPixels);
            check(visible(text(activity.getWindow().getDecorView(),"菜单")),"fullscreen menu reachable");
        });
    }
    private void checkReleaseParser() throws Exception {
        org.json.JSONObject json=new org.json.JSONObject("{\"tag_name\":\"v1.2.0\",\"assets\":[]}");
        org.json.JSONObject asset=new org.json.JSONObject();asset.put("name","StarPass-Router-1.2.0.apk");asset.put("size",12345);
        asset.put("browser_download_url","https://github.com/LONG10222/starpass-router-android/releases/download/v1.2.0/StarPass-Router-1.2.0.apk");
        asset.put("digest","sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");json.getJSONArray("assets").put(asset);
        check(AppUpdater.parseRelease(json,"1.1.0").version.equals("1.2.0"),"parse newer release");
        check(AppUpdater.parseRelease(json,"1.2.0")==null,"same version ignored");
        json.put("prerelease",true);check(AppUpdater.parseRelease(json,"1.1.0")==null,"preview ignored");json.put("prerelease",false);
        asset.put("digest",org.json.JSONObject.NULL);boolean rejected=false;
        try{AppUpdater.parseRelease(json,"1.1.0");}catch(Exception e){rejected=true;}check(rejected,"missing digest rejected");
        android.content.pm.ProviderInfo provider=getTargetContext().getPackageManager().resolveContentProvider(getTargetContext().getPackageName()+".updates",0);
        check(provider!=null&&!provider.exported&&provider.grantUriPermissions,"private update provider");
        boolean wrongUri=false;try{getTargetContext().getContentResolver().getType(android.net.Uri.parse("content://"+getTargetContext().getPackageName()+".updates/../router_profile"));}catch(Exception e){wrongUri=true;}
        check(wrongUri,"provider blocks non-update paths");
        boolean readOnly=false;try{getTargetContext().getContentResolver().openFileDescriptor(UpdateProvider.uri(getTargetContext()),"w");}catch(Exception e){readOnly=true;}check(readOnly,"provider blocks write access");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            site=arguments.getString("site","http://10.0.2.2:18086/cgi-bin/luci/");
            String phase=arguments.getString("phase","initial");
            if(phase.equals("real")){
                File provision=new File(getTargetContext().getFilesDir(),"test-profile.json");
                if(provision.exists()){
                    org.json.JSONObject value=new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(provision.toPath()),StandardCharsets.UTF_8));
                    check(provision.delete(),"private provision removed");
                    Profile real=new Profile(SitePolicy.normalize(value.getString("site")),value.getString("user"),value.getString("password"));
                    new CredentialStore(getTargetContext()).save(real);
                }
                check(new CredentialStore(getTargetContext()).load()!=null,"saved profile exists");activity=launch();
                boolean authenticated=false;
                for(int i=0;i<250&&!authenticated;i++){
                    java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
                    AtomicReference<Boolean> loaded=new AtomicReference<>(false);
                    ui(()->((WebView)activity.findViewById(MainActivity.WEB_ID)).evaluateJavascript("!!document.getElementById('cbi-portalproxy_ui') && !document.querySelector('input[name=luci_password]') && !!document.querySelector('[data-sp=version]') && document.querySelector('[data-sp=version]').textContent.trim()==='0.4.2'",v->{loaded.set("true".equals(v));latch.countDown();}));
                    latch.await(1,java.util.concurrent.TimeUnit.SECONDS);authenticated=loaded.get();if(!authenticated)Thread.sleep(100);
                }
                check(authenticated,"actual LuCI authenticated");screenshot("real-luci.png");
                result.putString("result","PASS real router login, original StarPass page rendered ("+checks+" assertions)");finish(Activity.RESULT_OK,result);return;
            }
            activity=launch();Thread.sleep(700);
            checkReleaseParser();
            if(phase.equals("initial")){
                checkLayout();screenshot("login-portrait.png");
                submit("wrong-example");awaitText("登录未完成");Thread.sleep(700);
                submit("example!x&\"\\示例");awaitAuthenticated();screenshot("web-page.png");
                checkFullscreen();screenshot("fullscreen-portrait.png");
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);Thread.sleep(1000);checkFullscreen();screenshot("fullscreen-landscape.png");
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);Thread.sleep(1000);checkFullscreen();
                menu("退出全屏");
                ui(()->{WebView v=activity.findViewById(MainActivity.WEB_ID);int[] xy=new int[2];v.getLocationOnScreen(xy);check(xy[1]>0,"normal toolbar restored");});
                menu("进入全屏");checkFullscreen();
                Profile profile=new CredentialStore(getTargetContext()).load();check(profile!=null&&profile.password.equals("example!x&\"\\示例"),"encrypted profile round trip");
                String encrypted=getTargetContext().getSharedPreferences("router_profile",0).getString("encrypted","");
                check(!encrypted.contains(profile.password)&&!encrypted.contains("10.0.2.2"),"no plaintext profile values");
                String cookies=CookieManager.getInstance().getCookie(site);check(cookies!=null&&cookies.contains("sysauth"),"cookie present");
                menu("登录设置");Thread.sleep(200);checkLayout();
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);Thread.sleep(800);checkLayout();screenshot("login-landscape.png");
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);Thread.sleep(800);checkLayout();
                submit(profile.password);awaitAuthenticated();
                String after=CookieManager.getInstance().getCookie(site);check(cookies.equals(after),"settings keep cookie");
            }else if(phase.equals("reopen")||phase.equals("expired")){
                awaitAuthenticated();check(true,"reopen/expired authentication restored");checkFullscreen();
                screenshot(phase+"-page.png");
            }
            result.putString("result","PASS "+phase+" ("+checks+" assertions)");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("result","FAIL "+error.getClass().getSimpleName()+": "+error.getMessage());finish(Activity.RESULT_CANCELED,result);}
    }
}
