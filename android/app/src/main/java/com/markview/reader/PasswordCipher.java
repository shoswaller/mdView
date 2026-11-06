package com.markview.reader;

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

final class PasswordCipher {
    private static final String KEY_ALIAS = "markview.sftp.passwords";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private PasswordCipher() { }

    private static SecretKey getKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
    }

    static JSONObject encrypt(String password) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        return new JSONObject()
                .put("passwordIv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .put("encryptedPassword", Base64.encodeToString(encrypted, Base64.NO_WRAP));
    }

    static String decrypt(JSONObject profile) throws Exception {
        String encodedIv = profile.optString("passwordIv");
        String encodedPassword = profile.optString("encryptedPassword");
        if (encodedIv.isEmpty() || encodedPassword.isEmpty()) {
            throw new IllegalArgumentException("未保存可用密码，请编辑服务器并重新输入密码");
        }
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(encodedPassword, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }
}
