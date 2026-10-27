package org.starpass.router;

import java.net.URI;
import java.util.Locale;

/** Pure update policy, shared by the release parser and local regression tests. */
final class UpdatePolicy {
    static final String REPOSITORY="LONG10222/starpass-router-android";
    static final String LATEST="https://api.github.com/repos/"+REPOSITORY+"/releases/latest";
    static final long MAX_APK_BYTES=50L*1024*1024;
    static String version(String tag){
        String value=tag.startsWith("v")?tag.substring(1):tag;
        if(!value.matches("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})"))throw new IllegalArgumentException("版本格式无效");
        return value;
    }
    static boolean newer(String candidate,String installed){
        String[] a=version(candidate).split("\\."),b=version(installed).split("\\.");
        for(int i=0;i<3;i++){int delta=Integer.compare(Integer.parseInt(a[i]),Integer.parseInt(b[i]));if(delta!=0)return delta>0;}return false;
    }
    static boolean releaseAsset(String url,String version){
        try{
            URI uri=URI.create(url);
            return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getPort()==-1&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null
                &&uri.getRawPath().equals("/"+REPOSITORY+"/releases/download/v"+version+"/StarPass-Router-"+version+".apk");
        }catch(Exception e){return false;}
    }
    static boolean downloadHost(String url){
        try{
            URI uri=URI.create(url);String host=uri.getHost();
            return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&uri.getFragment()==null&&(uri.getPort()==-1||uri.getPort()==443)
                &&("github.com".equals(host)||"release-assets.githubusercontent.com".equals(host)||"objects.githubusercontent.com".equals(host));
        }catch(Exception e){return false;}
    }
    static String sha256(String digest){
        if(digest==null||!digest.matches("sha256:[0-9a-fA-F]{64}"))throw new IllegalArgumentException("发布文件缺少 SHA-256 校验值，请维护者重新上传 APK");
        return digest.substring(7).toLowerCase(Locale.ROOT);
    }
}
