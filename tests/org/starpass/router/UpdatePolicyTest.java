package org.starpass.router;

public final class UpdatePolicyTest {
    private static int checks;
    private static void check(boolean result){checks++;if(!result)throw new AssertionError("Check "+checks);}
    private static void invalid(String value){boolean rejected=false;try{UpdatePolicy.version(value);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
    public static void main(String[] args){
        check(UpdatePolicy.newer("v1.1.0","1.0.0"));check(UpdatePolicy.newer("1.10.0","1.9.9"));
        check(!UpdatePolicy.newer("1.1.0","1.1.0"));check(!UpdatePolicy.newer("1.0.9","1.1.0"));check(UpdatePolicy.newer("2.0.0","1.99.99"));
        for(String value:new String[]{"","v","1.2","1.2.3-beta","01.2.3","-1.2.3","1.2.3.4","999999999.0.0","1.2.3\n"})invalid(value);
        String base="https://github.com/LONG10222/starpass-router-android/releases/download/v1.1.0/StarPass-Router-1.1.0.apk";
        check(UpdatePolicy.releaseAsset(base,"1.1.0"));
        for(String value:new String[]{base.replace("https:","http:"),base.replace("github.com","github.com.evil.test"),base.replace("LONG10222","attacker"),base+"?token=foo",base+"#fragment",base.replace("github.com","user@github.com"),base.replace("v1.1.0/","v1.2.0/"),base.replace("github.com","github.com:443")})check(!UpdatePolicy.releaseAsset(value,"1.1.0"));
        check(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/github-production-release-asset/x?token=123"));
        for(String value:new String[]{"http://github.com/x","https://release-assets.githubusercontent.com.evil.test/x","https://127.0.0.1/x","https://user@github.com/x","https://github.com:444/x","file:///data/private.apk"})check(!UpdatePolicy.downloadHost(value));
        check(UpdatePolicy.sha256("sha256:"+"A".repeat(64)).equals("a".repeat(64)));
        for(String value:new String[]{"","sha256:123","sha1:"+"a".repeat(64),"sha256:"+"g".repeat(64)}){
            boolean rejected=false;try{UpdatePolicy.sha256(value);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        }
        System.out.println("PASS update policy ("+checks+" assertions)");
    }
}
