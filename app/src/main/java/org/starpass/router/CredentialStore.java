package org.starpass.router;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class CredentialStore {
    private static final String ALIAS="starpass_router_profile_v1";
    private final SharedPreferences preferences;
    CredentialStore(Context context){preferences=context.getSharedPreferences("router_profile",Context.MODE_PRIVATE);}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    void save(Profile value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        JSONObject object=new JSONObject();object.put("site",value.site);object.put("user",value.user);object.put("password",value.password);
        byte[] encrypted=cipher.doFinal(object.toString().getBytes(StandardCharsets.UTF_8));
        if(!preferences.edit().putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP))
            .putString("encrypted",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())throw new Exception("save failed");
    }
    Profile load() throws Exception {
        String encrypted=preferences.getString("encrypted",null);if(encrypted==null)return null;
        byte[] iv=Base64.decode(preferences.getString("iv",""),Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        JSONObject object=new JSONObject(new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8));
        return new Profile(SitePolicy.normalize(object.getString("site")),object.getString("user"),object.getString("password"));
    }
}
