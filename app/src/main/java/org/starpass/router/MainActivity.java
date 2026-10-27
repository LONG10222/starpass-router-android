package org.starpass.router;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    static final int SITE_ID=1001,USER_ID=1002,PASSWORD_ID=1003,LOGIN_ID=1004,WEB_ID=1005;
    static final int FILE_PICK=41,SAVE_DOWNLOAD=42;
    private static final int PURPLE=0xff6554c0,TEXT=0xff29223f,NOTE=0xff6c647e,BG=0xfff6f4fb;
    private LinearLayout root,browser;
    private FrameLayout surface;
    private View toolbar;
    private Button floatingMenu;
    private boolean fullscreen=true;
    private AppUpdater updater;
    private View loginPanel;
    private TextView message,browserStatus;
    private EditText siteInput,userInput,passwordInput;
    private WebView web;
    private ProgressBar progress;
    private CredentialStore store;
    private Profile profile;
    private String loginScript,compatScript;
    private boolean attempted=false,suspended=false,destroyed=false;
    private int pageGeneration=0;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingDownload;
    private final ExecutorService downloads=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        fullscreen=getPreferences(0).getBoolean("fullscreen",true);
        updater=new AppUpdater(this,state);
        store=new CredentialStore(this);
        try(InputStream in=getAssets().open("login.js")){loginScript=readAsset(in);}
        catch(Exception e){throw new IllegalStateException("Missing login helper");}
        try(InputStream in=getAssets().open("compat.js")){compatScript=readAsset(in);}
        catch(Exception e){throw new IllegalStateException("Missing compatibility helper");}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        surface=new FrameLayout(this);surface.setBackgroundColor(BG);
        surface.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(surface);
        if(Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams attrs=getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode=Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            surface.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                int ime=insets.getInsets(WindowInsets.Type.ime()).bottom;
                boolean full=isBrowserFullscreen();
                root.setPadding(full?0:bars.left,full?0:bars.top,full?0:bars.right,full?ime:Math.max(bars.bottom,ime));
                if(floatingMenu!=null){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)floatingMenu.getLayoutParams();p.rightMargin=dp(12)+bars.right;p.bottomMargin=dp(12)+Math.max(bars.bottom,ime);floatingMenu.setLayoutParams(p);}
                return insets;
            });
        } else {
            surface.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            surface.setOnApplyWindowInsetsListener((v,insets)->{
                boolean full=isBrowserFullscreen();
                root.setPadding(full?0:insets.getSystemWindowInsetLeft(),full?0:insets.getSystemWindowInsetTop(),full?0:insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
                return insets;
            });
        }
        String error=null;
        try{profile=store.load();}catch(Exception e){error="原登录信息暂时无法读取，请重新填写。";}
        createLogin();createBrowser();
        floatingMenu=button("菜单",false);floatingMenu.setContentDescription("全屏菜单");
        FrameLayout.LayoutParams menuParams=new FrameLayout.LayoutParams(dp(64),dp(48),Gravity.BOTTOM|Gravity.RIGHT);
        menuParams.setMargins(dp(12),dp(12),dp(12),dp(12));surface.addView(floatingMenu,menuParams);
        floatingMenu.setOnClickListener(v->showBrowserMenu());
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::navigateBack);
        if(profile!=null)connect(profile);else showLogin(error);
    }
    private static String readAsset(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    private GradientDrawable background(int color,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private LinearLayout.LayoutParams matchWrap(){return new LinearLayout.LayoutParams(-1,-2);}
    private void gap(LinearLayout parent,int height){View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height)));}
    private EditText input(LinearLayout parent,String title,String hint,int id,int inputType) {
        TextView label=label(title,14,TEXT);label.setTypeface(null,Typeface.BOLD);parent.addView(label,matchWrap());gap(parent,8);
        EditText edit=new EditText(this);edit.setId(id);edit.setSingleLine(true);edit.setTextSize(16);edit.setTextColor(TEXT);edit.setHintTextColor(NOTE);
        edit.setHint(hint);edit.setInputType(inputType);edit.setPadding(dp(14),dp(13),dp(14),dp(13));edit.setBackground(background(Color.WHITE,0xffded8ec));
        edit.setSaveEnabled(false);edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        edit.setMinHeight(dp(54));parent.addView(edit,new LinearLayout.LayoutParams(-1,-2));gap(parent,22);return edit;
    }
    private Button button(String title,boolean primary){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(primary?Color.WHITE:PURPLE);b.setMinHeight(dp(44));b.setBackground(background(primary?PURPLE:0xffefebfa,0));return b;}
    private void createLogin() {
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER_HORIZONTAL);center.setPadding(dp(24),dp(38),dp(24),dp(24));
        LinearLayout form=new LinearLayout(this){
            @Override protected void onMeasure(int widthSpec,int heightSpec){
                int cap=Math.min(View.MeasureSpec.getSize(widthSpec),dp(480));
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.getMode(widthSpec)),heightSpec);
            }
        };form.setOrientation(LinearLayout.VERTICAL);form.setPadding(dp(4),0,dp(4),0);
        LinearLayout.LayoutParams formParams=new LinearLayout.LayoutParams(-1,-2);formParams.gravity=Gravity.CENTER_HORIZONTAL;
        // Bound the tablet form; phone fields remain one vertical column.
        TextView title=label("连接软路由",28,TEXT);title.setTypeface(null,Typeface.BOLD);form.addView(title);gap(form,8);
        form.addView(label("登录后使用完整管理网页",14,NOTE));gap(form,32);
        siteInput=input(form,"网站地址","http://192.168.1.1",SITE_ID,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        userInput=input(form,"用户名","root",USER_ID,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        passwordInput=input(form,"密码","输入管理密码",PASSWORD_ID,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        userInput.setText("root");
        CheckBox reveal=new CheckBox(this);reveal.setText("显示密码");reveal.setTextColor(NOTE);reveal.setTextSize(14);
        reveal.setOnCheckedChangeListener((v,checked)->{int selection=passwordInput.getSelectionStart();passwordInput.setInputType(InputType.TYPE_CLASS_TEXT|(checked?InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_TEXT_VARIATION_PASSWORD));passwordInput.setSelection(Math.max(0,Math.min(selection,passwordInput.length())));});
        form.addView(reveal);gap(form,14);
        Button login=button("登录并记住",true);login.setId(LOGIN_ID);login.setMinHeight(dp(52));form.addView(login,new LinearLayout.LayoutParams(-1,-2));
        gap(form,16);form.addView(label("登录信息保存在本机，下次自动进入。\n请让手机连接同一局域网。",13,NOTE));
        gap(form,16);Button updates=button("检查更新 · v"+updater.currentVersion(),false);form.addView(updates,matchWrap());updates.setOnClickListener(v->updater.check());
        message=label("",14,0xffa44524);gap(form,12);form.addView(message,matchWrap());
        login.setOnClickListener(v->saveAndConnect());
        passwordInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        passwordInput.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO || event!=null&&event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER){saveAndConnect();return true;}return false;
        });
        siteInput.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                if(profile!=null&&!passwordInput.getText().toString().isEmpty()) {
                    try {if(!SitePolicy.sameOrigin(profile.site,SitePolicy.normalize(s.toString()))){passwordInput.setText("");message.setText("地址已变更，请输入这台设备的密码。");}}
                    catch(IllegalArgumentException ignored){}
                }
            }
            public void afterTextChanged(Editable e){}
        });
        center.addView(form,formParams);
        scroll.addView(center);loginPanel=scroll;root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void saveAndConnect() {
        try {
            String site=SitePolicy.normalize(siteInput.getText().toString());
            String user=userInput.getText().toString().trim(),password=passwordInput.getText().toString();
            if(user.isEmpty()||user.length()>128||user.contains("\n")||user.contains("\r")){message.setText("请填写用户名。");return;}
            if(password.isEmpty()||password.length()>4096){message.setText("请填写管理密码。");return;}
            Profile next=new Profile(site,user,password);store.save(next);profile=next;
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(passwordInput.getWindowToken(),0);
            connect(profile);
        }catch(IllegalArgumentException e){message.setText(e.getMessage());}
        catch(Exception e){message.setText("无法保存登录信息，请重试；尚未发送密码。");}
    }
    private void showLogin(String reason) {
        loginPanel.setVisibility(View.VISIBLE);browser.setVisibility(View.GONE);
        if(profile!=null){siteInput.setText(profile.site);userInput.setText(profile.user);passwordInput.setText(profile.password);}
        message.setText(reason==null?"":reason);
        applyDisplayMode();
    }
    private void createBrowser() {
        browser=new LinearLayout(this);browser.setOrientation(LinearLayout.VERTICAL);
        LinearLayout toolbar=new LinearLayout(this);this.toolbar=toolbar;toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setPadding(dp(12),dp(6),dp(12),dp(6));
        TextView title=label("星渡路由",16,TEXT);title.setTypeface(null,Typeface.BOLD);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button refresh=button("刷新",false);Button settings=button("菜单",false);
        toolbar.addView(refresh,new LinearLayout.LayoutParams(dp(64),dp(42)));
        LinearLayout.LayoutParams settingParams=new LinearLayout.LayoutParams(dp(92),dp(42));settingParams.leftMargin=dp(8);toolbar.addView(settings,settingParams);
        browser.addView(toolbar);browserStatus=label("",12,NOTE);browserStatus.setPadding(dp(14),0,dp(14),dp(5));browser.addView(browserStatus);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);browser.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        web=new WebView(this);web.setId(WEB_ID);web.setSaveEnabled(false);web.setBackgroundColor(Color.WHITE);
        WebSettings config=web.getSettings();config.setJavaScriptEnabled(true);config.setDomStorageEnabled(true);
        config.setAllowFileAccess(false);config.setAllowContentAccess(true);config.setJavaScriptCanOpenWindowsAutomatically(false);
        config.setSupportMultipleWindows(false);config.setUseWideViewPort(true);config.setLoadWithOverviewMode(true);
        config.setBuiltInZoomControls(true);config.setDisplayZoomControls(false);config.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){
                String target=r.getUrl().toString();
                if(profile!=null&&SitePolicy.sameOrigin(profile.site,target)){
                    if(target.toLowerCase(java.util.Locale.ROOT).contains("/logout"))suspended=true;
                    return false;
                }
                if(!r.isForMainFrame())return !("https".equals(r.getUrl().getScheme())||"http".equals(r.getUrl().getScheme()));
                openExternal(target);return true;
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){pageGeneration++;browserStatus.setText("正在连接…");}
            @Override public void onPageCommitVisible(WebView view,String url){
                if(profile!=null&&SitePolicy.sameOrigin(profile.site,url))view.evaluateJavascript(compatScript,null);
            }
            @Override public void onPageFinished(WebView view,String url){
                CookieManager.getInstance().flush();
                if(profile==null||!SitePolicy.sameOrigin(profile.site,url)||!SitePolicy.sameOrigin(profile.site,web.getUrl()))return;
                browserStatus.setText(URI.create(profile.site).getAuthority());
                int generation=pageGeneration;view.evaluateJavascript(compatScript,ignored->inspectLogin(generation,url));
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){
                if(r.isForMainFrame())browserStatus.setText("连接失败，请确认手机与软路由在同一局域网，可点刷新重试。");
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error){handler.cancel();browserStatus.setText("HTTPS 证书未通过验证，请检查地址或证书。");}
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int value){progress.setProgress(value);progress.setVisibility(value==100?View.GONE:View.VISIBLE);}
            @Override public boolean onJsAlert(WebView v,String url,String text,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(text).setPositiveButton("确定",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onJsConfirm(WebView v,String url,String text,JsResult result){new AlertDialog.Builder(MainActivity.this).setMessage(text).setPositiveButton("确定",(d,w)->result.confirm()).setNegativeButton("取消",(d,w)->result.cancel()).setOnCancelListener(d->result.cancel()).show();return true;}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(profile==null||!SitePolicy.sameOrigin(profile.site,v.getUrl()))return false;
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=callback;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                try{startActivityForResult(intent,FILE_PICK);}catch(ActivityNotFoundException e){callback.onReceiveValue(null);fileCallback=null;}
                return true;
            }
        });
        web.setDownloadListener((url,agent,disposition,mime,length)->{
            if(profile==null||!SitePolicy.sameOrigin(profile.site,url)){openExternal(url);return;}
            pendingDownload=url;
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime==null?"application/octet-stream":mime);
            intent.putExtra(Intent.EXTRA_TITLE,URLUtil.guessFileName(url,disposition,mime));
            try{startActivityForResult(intent,SAVE_DOWNLOAD);}catch(ActivityNotFoundException e){pendingDownload=null;browserStatus.setText("系统文件保存界面不可用。");}
        });
        browser.addView(web,new LinearLayout.LayoutParams(-1,0,1));root.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
        settings.setOnClickListener(v->showBrowserMenu());refresh.setOnClickListener(v->refreshPage());
    }
    private void refreshPage(){if(profile!=null){attempted=false;suspended=false;if(SitePolicy.sameOrigin(profile.site,web.getUrl()))web.reload();else web.loadUrl(profile.site);}}
    private void showBrowserMenu(){
        new AlertDialog.Builder(this).setTitle("星渡路由 · v"+updater.currentVersion())
            .setItems(new String[]{fullscreen?"退出全屏":"进入全屏","刷新网页","登录设置","检查更新"},(d,index)->{
                if(index==0){fullscreen=!fullscreen;getPreferences(0).edit().putBoolean("fullscreen",fullscreen).apply();applyDisplayMode();}
                else if(index==1)refreshPage();else if(index==2)showLogin(null);else updater.check();
            }).show();
    }
    private boolean isBrowserFullscreen(){return fullscreen&&browser!=null&&browser.getVisibility()==View.VISIBLE;}
    private void applyDisplayMode(){
        boolean full=isBrowserFullscreen();toolbar.setVisibility(full?View.GONE:View.VISIBLE);
        browserStatus.setVisibility(full?View.GONE:View.VISIBLE);floatingMenu.setVisibility(full?View.VISIBLE:View.GONE);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(full)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}
        }else{
            int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if(full)flags|=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            surface.setSystemUiVisibility(flags);
        }
        surface.requestApplyInsets();
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&floatingMenu!=null)applyDisplayMode();}
    private String script(boolean submit) {
        String args=JSONObject.quote(SitePolicy.origin(profile.site))+","+JSONObject.quote(submit?profile.user:"")+","+JSONObject.quote(submit?profile.password:"")+","+submit;
        return loginScript.replace("/*ARGS*/",args);
    }
    private void inspectLogin(int generation,String url) {
        if(suspended)return;
        web.evaluateJavascript(script(false),result->{
            if(destroyed||profile==null||generation!=pageGeneration||!url.equals(web.getUrl()))return;
            if("\"not-login\"".equals(result)){attempted=false;return;}
            if(!"\"login\"".equals(result))return;
            if(attempted){showLogin("登录未完成，请检查用户名和密码后重试。");return;}
            attempted=true;browserStatus.setText("正在登录…");
            web.evaluateJavascript(script(true),null);
        });
    }
    private void connect(Profile value) {
        attempted=false;suspended=false;pageGeneration++;loginPanel.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);message.setText("");
        // Reuse an existing authenticated page when opening settings; never call logout.
        web.loadUrl(value.site);
        applyDisplayMode();
    }
    private void openExternal(String target) {
        try {Uri uri=Uri.parse(target);if(!"https".equals(uri.getScheme())&&!"http".equals(uri.getScheme()))return;
            startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException ignored){}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==FILE_PICK&&fileCallback!=null){
            Uri[] selected=null;
            if(result==RESULT_OK&&data!=null){if(data.getClipData()!=null){selected=new Uri[data.getClipData().getItemCount()];for(int i=0;i<selected.length;i++)selected[i]=data.getClipData().getItemAt(i).getUri();}else if(data.getData()!=null)selected=new Uri[]{data.getData()};}
            fileCallback.onReceiveValue(selected);fileCallback=null;
        }else if(request==SAVE_DOWNLOAD){
            String url=pendingDownload;pendingDownload=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&url!=null)saveDownload(url,data.getData());
        }
    }
    private void saveDownload(String url,Uri output) {
        final String base=profile.site,cookies=CookieManager.getInstance().getCookie(url),agent=web.getSettings().getUserAgentString();
        browserStatus.setText("正在保存文件…");
        downloads.execute(()->{
            HttpURLConnection connection=null;
            try{
                String target=url;
                for(int redirects=0;;redirects++){
                    if(redirects>4||!SitePolicy.sameOrigin(base,target))throw new Exception();
                    connection=(HttpURLConnection)new URL(target).openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(15000);connection.setReadTimeout(30000);
                    connection.setRequestProperty("User-Agent",agent);if(cookies!=null)connection.setRequestProperty("Cookie",cookies);
                    int code=connection.getResponseCode();
                    if(code==301||code==302||code==303||code==307||code==308){String next=connection.getHeaderField("Location");connection.disconnect();target=new URL(new URL(target),next).toString();continue;}
                    if(code!=200)throw new Exception();break;
                }
                try(InputStream in=connection.getInputStream();OutputStream out=getContentResolver().openOutputStream(output,"w")){
                    if(out==null)throw new Exception();byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
                }
                runOnUiThread(()->browserStatus.setText("文件已保存。"));
            }catch(Exception e){runOnUiThread(()->browserStatus.setText("保存未完成，请重试；目标文件可能不完整。"));}
            finally{if(connection!=null)connection.disconnect();}
        });
    }
    private void navigateBack(){
        if(loginPanel.getVisibility()==View.VISIBLE&&profile!=null&&web.getUrl()!=null){loginPanel.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);applyDisplayMode();return;}
        if(browser.getVisibility()==View.VISIBLE&&web.canGoBack())web.goBack();else finish();
    }
    @Override public void onBackPressed(){navigateBack();}
    @Override protected void onPause(){super.onPause();if(web!=null){CookieManager.getInstance().flush();web.onPause();}}
    @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();if(updater!=null)updater.onResume();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);updater.saveState(state);}
    @Override protected void onDestroy(){destroyed=true;if(updater!=null)updater.close();if(fileCallback!=null)fileCallback.onReceiveValue(null);downloads.shutdown();if(web!=null){((ViewGroup)web.getParent()).removeView(web);web.destroy();}super.onDestroy();}
}
