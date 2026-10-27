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
            if(phase.equals("initial")){
                checkLayout();screenshot("login-portrait.png");
                submit("wrong-example");awaitText("登录未完成");Thread.sleep(700);
                submit("example!x&\"\\示例");awaitAuthenticated();screenshot("web-page.png");
                Profile profile=new CredentialStore(getTargetContext()).load();check(profile!=null&&profile.password.equals("example!x&\"\\示例"),"encrypted profile round trip");
                String encrypted=getTargetContext().getSharedPreferences("router_profile",0).getString("encrypted","");
                check(!encrypted.contains(profile.password)&&!encrypted.contains("10.0.2.2"),"no plaintext profile values");
                String cookies=CookieManager.getInstance().getCookie(site);check(cookies!=null&&cookies.contains("sysauth"),"cookie present");
                ui(()->text(activity.getWindow().getDecorView(),"登录设置").performClick());Thread.sleep(200);checkLayout();
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);Thread.sleep(800);checkLayout();screenshot("login-landscape.png");
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);Thread.sleep(800);checkLayout();
                submit(profile.password);awaitAuthenticated();
                String after=CookieManager.getInstance().getCookie(site);check(cookies.equals(after),"settings keep cookie");
            }else if(phase.equals("reopen")||phase.equals("expired")){
                awaitAuthenticated();check(true,"reopen/expired authentication restored");
                screenshot(phase+"-page.png");
            }
            result.putString("result","PASS "+phase+" ("+checks+" assertions)");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("result","FAIL "+error.getClass().getSimpleName()+": "+error.getMessage());finish(Activity.RESULT_CANCELED,result);}
    }
}
