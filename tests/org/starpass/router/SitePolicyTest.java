package org.starpass.router;
public final class SitePolicyTest {
    private static int count=0;
    private static void yes(boolean value){count++;if(!value)throw new AssertionError("case "+count);}
    public static void main(String[] args){
        yes(SitePolicy.normalize("192.168.1.1").equals("http://192.168.1.1/cgi-bin/luci/"));
        yes(SitePolicy.normalize("https://10.0.0.1:8443/cgi-bin/luci/admin/services/portalproxy").endsWith("/portalproxy"));
        yes(SitePolicy.sameOrigin("http://192.168.1.1/","http://192.168.1.1:80/a"));
        for(String target:new String[]{"https://192.168.1.1/","http://192.168.1.1:81/","http://192.168.1.10/","http://192.168.1.1.evil.example/","javascript:alert(1)","http://root@192.168.1.1/"})yes(!SitePolicy.sameOrigin("http://192.168.1.1/",target));
        for(String target:new String[]{"https://example.com","127.0.0.1","169.254.1.2","100.64.0.1","192.168.001.1","10.0.0.256","192.168.1.1:0","192.168.1.1:99999","192.168.1.1:abc","ftp://192.168.1.1","http://user:secret@192.168.1.1","192.168.1.1/?token=abc","192.168.1.1/#x","192.168.1.1/cgi-bin/luci-evil","192.168.1.1/cgi-bin/luci/admin/logout","192.168.1.1/cgi-bin/luci/admin/%6cogout","192.168.1.1/cgi-bin/luci/../logout","192.168.1.1/cgi-bin/luci/;stok=private"}){
            boolean rejected=false;try{SitePolicy.normalize(target);}catch(IllegalArgumentException expected){rejected=true;}yes(rejected);
        }
        for(String target:new String[]{"10.255.255.254","172.16.0.1","172.31.255.254","192.168.0.1"})yes(SitePolicy.privateIpv4(target));
        yes(!SitePolicy.privateIpv4("172.32.0.1"));yes(!SitePolicy.privateIpv4("172.15.0.1"));
        System.out.println("SitePolicy: "+count+" assertions passed");
    }
}
