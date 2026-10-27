package org.starpass.router;

import java.net.URI;
import java.util.Locale;

/** Credentials are scoped to one explicit private IPv4 origin. No DNS guessing. */
public final class SitePolicy {
    private SitePolicy() {}
    public static String normalize(String raw) {
        String value=raw==null?"":raw.trim();
        if(value.length()>2048 || value.isEmpty() || value.indexOf('\\')>=0) throw new IllegalArgumentException("请输入软路由的局域网地址");
        if(!value.contains("://"))value="http://"+value;
        try {
            URI u=new URI(value);
            String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT);
            if(!scheme.equals("http")&&!scheme.equals("https"))throw new Exception();
            if(u.getRawUserInfo()!=null || u.getRawFragment()!=null || u.getRawQuery()!=null)throw new Exception();
            String host=u.getHost();
            if(!privateIpv4(host) || u.getPort()==0 || u.getPort()>65535 || u.getPort() < -1)throw new Exception();
            String path=u.getRawPath();
            if(path==null||path.isEmpty()||path.equals("/"))path="/cgi-bin/luci/";
            if(!(path.equals("/cgi-bin/luci")||path.startsWith("/cgi-bin/luci/")) || u.getPath().toLowerCase(Locale.ROOT).contains("logout") || u.getPath().contains(";") || u.getPath().contains(".."))throw new Exception();
            return scheme+"://"+host+(u.getPort()!=-1?":"+u.getPort():"")+path;
        } catch(Exception e) { throw new IllegalArgumentException("使用局域网 IPv4 地址，例如 http://192.168.1.1，不要填写账号、查询参数或退出地址"); }
    }
    public static boolean privateIpv4(String host) {
        if(host==null)return false;
        String[] parts=host.split("\\.",-1);if(parts.length!=4)return false;
        int[] n=new int[4];
        try { for(int i=0;i<4;i++) {
            if(!parts[i].matches("0|[1-9][0-9]{0,2}"))return false;
            n[i]=Integer.parseInt(parts[i]);if(n[i]>255)return false;
        }}catch(Exception e){return false;}
        return n[0]==10 || n[0]==172&&n[1]>=16&&n[1]<=31 || n[0]==192&&n[1]==168;
    }
    public static String origin(String value) {
        try {
            URI u=URI.create(value);String s=u.getScheme().toLowerCase(Locale.ROOT);
            int port=u.getPort()==-1?(s.equals("https")?443:80):u.getPort();
            if(!s.equals("http")&&!s.equals("https")||u.getHost()==null||u.getRawUserInfo()!=null)return "";
            return s+"://"+u.getHost().toLowerCase(Locale.ROOT)+":"+port;
        }catch(Exception e){return "";}
    }
    public static boolean sameOrigin(String base,String candidate) {
        String origin=origin(base);return !origin.isEmpty()&&origin.equals(origin(candidate));
    }
}
