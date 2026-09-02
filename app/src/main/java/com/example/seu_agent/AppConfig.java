package com.example.seu_agent;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;


public class AppConfig {

    private static final String PREFS = "app_config";


    private static final String KEY_LLM_MODEL = "llm_model";
    private static final String KEY_LLM_URL = "llm_url";
    /** 旧版本使用的明文字段，仅用于一次性迁移。 */
    private static final String KEY_API_KEY_LEGACY = "api_key";
    private static final String KEY_API_KEY_ENCRYPTED = "api_key_encrypted_v1";
    private static final String KEY_MODELS = "models";

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String API_KEY_ALIAS = "seu_agent_api_key_aes_v1";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    public static final String DEFAULT_SERVER_URL = "http://103.236.89.13:8003";
    public static final String DEFAULT_LLM_URL = "https://api.deepseek.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";


    private static final List<String> DEFAULT_MODELS =
            Arrays.asList( "deepseek-v4-flash", "deepseek-v4-pro");


    public static String getServerUrl(Context c) {
        return DEFAULT_SERVER_URL;
    }

    public static String getLlmModel(Context c) {
        return prefs(c).getString(KEY_LLM_MODEL, DEFAULT_MODEL);
    }


    public static void setModel(Context c, String model) {
        if (model == null || model.trim().isEmpty()) return;
        prefs(c).edit().putString(KEY_LLM_MODEL, model.trim()).apply();
    }

    public static String getLlmUrl(Context c) {
        return prefs(c).getString(KEY_LLM_URL, DEFAULT_LLM_URL);
    }

    public static String getApiKey(Context c) {
        SharedPreferences p = prefs(c);
        String encrypted = p.getString(KEY_API_KEY_ENCRYPTED, "");
        if (encrypted != null && !encrypted.isEmpty()) {
            try {
                return decryptApiKey(c, encrypted);
            } catch (Exception ignored) {
                // 密文损坏或 Keystore 密钥不可用时绝不回退为明文存储。
                return "";
            }
        }

        // 从旧版普通 SharedPreferences 一次性迁移；成功后立即删除明文字段。
        String legacy = p.getString(KEY_API_KEY_LEGACY, "");
        if (legacy == null || legacy.isEmpty()) return "";
        try {
            String migrated = encryptApiKey(c, legacy);
            p.edit()
                    .putString(KEY_API_KEY_ENCRYPTED, migrated)
                    .remove(KEY_API_KEY_LEGACY)
                    .commit();
            return legacy;
        } catch (Exception ignored) {
            // 迁移失败时不删除旧值，避免用户凭据不可恢复；下次读取会再次迁移。
            return legacy;
        }
    }


    public static List<String> getModels(Context c) {
        String raw = prefs(c).getString(KEY_MODELS, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>(DEFAULT_MODELS);
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_MODELS);
        }
        return out;
    }


    public static void addModel(Context c, String model) {
        if (model == null || model.trim().isEmpty()) return;
        String m = model.trim();
        List<String> list = getModels(c);
        if (!list.contains(m)) list.add(m);
        saveModels(c, list);
    }

    public static void removeModel(Context c, String model) {
        if (model == null) return;
        List<String> list = getModels(c);
        list.remove(model);
        saveModels(c, list);
    }

    private static void saveModels(Context c, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs(c).edit().putString(KEY_MODELS, arr.toString()).apply();
    }


    /** 保存设置。API Key 加密失败时返回 false，且不会写入明文。 */
    public static boolean save(Context c, String model, String llmUrl, String apiKey) {
        String normalizedKey = apiKey == null ? "" : apiKey.trim();
        final String encrypted;
        try {
            encrypted = normalizedKey.isEmpty() ? "" : encryptApiKey(c, normalizedKey);
        } catch (Exception e) {
            return false;
        }

        SharedPreferences.Editor editor = prefs(c).edit()
                .putString(KEY_LLM_MODEL, model == null ? DEFAULT_MODEL : model.trim())
                .putString(KEY_LLM_URL, llmUrl == null ? "" : llmUrl.trim())
                .remove(KEY_API_KEY_LEGACY);
        if (encrypted.isEmpty()) {
            editor.remove(KEY_API_KEY_ENCRYPTED);
        } else {
            editor.putString(KEY_API_KEY_ENCRYPTED, encrypted);
        }
        return editor.commit();
    }

    /**
     * 密文格式：Base64(12-byte IV) + ':' + Base64(ciphertext+GCM tag)。
     * AES 密钥不可导出，只能由 Android Keystore 内部用于加解密。
     */
    private static String encryptApiKey(Context c, String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateApiKeySecret());
        cipher.updateAAD(c.getPackageName().getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":"
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private static String decryptApiKey(Context c, String stored) throws Exception {
        String[] parts = stored.split(":", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted API key");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateApiKeySecret(),
                new GCMParameterSpec(128, iv));
        cipher.updateAAD(c.getPackageName().getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateApiKeySecret() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(API_KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        generator.init(new KeyGenParameterSpec.Builder(
                API_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
