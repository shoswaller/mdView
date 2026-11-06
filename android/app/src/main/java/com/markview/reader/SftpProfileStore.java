package com.markview.reader;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class SftpProfileStore {
    private static final String PREFERENCES_NAME = "sftp_profiles";
    private static final String DATA_KEY = "data";

    private final SharedPreferences preferences;

    SftpProfileStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private JSONObject readData() throws JSONException {
        String stored = preferences.getString(DATA_KEY, null);
        if (stored == null) {
            return new JSONObject().put("version", 1).put("profiles", new JSONArray());
        }
        JSONObject data = new JSONObject(stored);
        if (data.optInt("version") != 1 || !(data.opt("profiles") instanceof JSONArray)) {
            throw new JSONException("配置格式不受支持");
        }
        return data;
    }

    private void writeData(JSONObject data) throws JSONException {
        if (!preferences.edit().putString(DATA_KEY, data.toString()).commit()) {
            throw new JSONException("无法保存 SFTP 服务器配置");
        }
    }

    private JSONObject normalize(JSONObject input) throws JSONException {
        String host = input.optString("host").trim();
        String username = input.optString("username").trim();
        String name = input.optString("name").trim();
        String initialPath = input.optString("initialPath").trim();
        int port = input.optInt("port", 22);
        if (initialPath.isEmpty()) {
            initialPath = "/";
        }
        if (host.isEmpty() || host.length() > 253 || host.matches(".*[\\s/\\\\].*")) {
            throw new IllegalArgumentException("请输入有效的主机名或 IP 地址");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须是 1 到 65535 之间的整数");
        }
        if (username.isEmpty() || username.length() > 256 || username.indexOf('\r') >= 0
                || username.indexOf('\n') >= 0 || username.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("请输入有效的用户名");
        }
        if (name.length() > 80 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("服务器名称不能超过 80 个字符");
        }
        if (initialPath.length() > 4096 || initialPath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("请输入有效的初始目录");
        }
        return new JSONObject()
                .put("name", name.isEmpty() ? host : name)
                .put("host", host)
                .put("port", port)
                .put("username", username)
                .put("initialPath", initialPath);
    }

    private JSONObject publicProfile(JSONObject profile) throws JSONException {
        return new JSONObject()
                .put("id", profile.getString("id"))
                .put("name", profile.optString("name"))
                .put("host", profile.getString("host"))
                .put("port", profile.getInt("port"))
                .put("username", profile.getString("username"))
                .put("initialPath", profile.optString("initialPath", "/"))
                .put("hasPassword", !profile.optString("encryptedPassword").isEmpty())
                .put("createdAt", profile.optLong("createdAt"))
                .put("updatedAt", profile.optLong("updatedAt"))
                .put("lastUsedAt", profile.optLong("lastUsedAt"));
    }

    synchronized JSONObject list() throws JSONException {
        JSONArray stored = readData().getJSONArray("profiles");
        List<JSONObject> profiles = new ArrayList<>();
        for (int index = 0; index < stored.length(); index++) {
            profiles.add(stored.getJSONObject(index));
        }
        profiles.sort(Comparator.comparingLong((JSONObject profile) -> profile.optLong("lastUsedAt"))
                .thenComparingLong(profile -> profile.optLong("updatedAt")).reversed());
        JSONArray result = new JSONArray();
        for (JSONObject profile : profiles) {
            result.put(publicProfile(profile));
        }
        return new JSONObject().put("success", true).put("encryptionAvailable", true)
                .put("profiles", result);
    }

    synchronized JSONObject save(JSONObject input, boolean markUsed) throws Exception {
        JSONObject data = readData();
        JSONArray profiles = data.getJSONArray("profiles");
        JSONObject values = normalize(input);
        String requestedId = input.optString("id");
        int existingIndex = -1;
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.getJSONObject(index);
            boolean same = !requestedId.isEmpty() ? requestedId.equals(profile.optString("id"))
                    : profile.optString("host").equalsIgnoreCase(values.getString("host"))
                            && profile.optInt("port") == values.getInt("port")
                            && profile.optString("username").equals(values.getString("username"));
            if (same) {
                existingIndex = index;
                break;
            }
        }
        JSONObject existing = existingIndex >= 0 ? profiles.getJSONObject(existingIndex) : null;
        long now = System.currentTimeMillis();
        String password = input.optString("password");
        String encryptedPassword = existing == null ? "" : existing.optString("encryptedPassword");
        String passwordIv = existing == null ? "" : existing.optString("passwordIv");
        if (!password.isEmpty()) {
            encryptedPassword = "";
            passwordIv = "";
            try {
                JSONObject encrypted = PasswordCipher.encrypt(password);
                encryptedPassword = encrypted.getString("encryptedPassword");
                passwordIv = encrypted.getString("passwordIv");
            } catch (Exception ignored) {
                // A successful connection can still be saved without its password.
            }
        }
        JSONObject profile = new JSONObject()
                .put("id", existing == null ? UUID.randomUUID().toString() : existing.getString("id"))
                .put("name", values.getString("name"))
                .put("host", values.getString("host"))
                .put("port", values.getInt("port"))
                .put("username", values.getString("username"))
                .put("initialPath", values.getString("initialPath"))
                .put("encryptedPassword", encryptedPassword)
                .put("passwordIv", passwordIv)
                .put("createdAt", existing == null ? now : existing.optLong("createdAt"))
                .put("updatedAt", now)
                .put("lastUsedAt", markUsed ? now : existing == null ? 0 : existing.optLong("lastUsedAt"));
        if (existingIndex >= 0) {
            profiles.put(existingIndex, profile);
        } else {
            profiles.put(profile);
        }
        writeData(data);
        return new JSONObject().put("profile", publicProfile(profile))
                .put("passwordSaved", !encryptedPassword.isEmpty());
    }

    synchronized JSONObject update(JSONObject input) throws Exception {
        String id = input.optString("id");
        JSONObject values = normalize(input);
        JSONArray profiles = readData().getJSONArray("profiles");
        boolean found = false;
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.getJSONObject(index);
            if (id.equals(profile.optString("id"))) {
                found = true;
            } else if (profile.optString("host").equalsIgnoreCase(values.getString("host"))
                    && profile.optInt("port") == values.getInt("port")
                    && profile.optString("username").equals(values.getString("username"))) {
                throw new IllegalArgumentException("相同主机、端口和用户名的服务器已存在");
            }
        }
        if (!found) {
            throw new IllegalArgumentException("服务器配置不存在，可能已被删除");
        }
        return save(input, false);
    }

    synchronized JSONObject connectionConfig(String id) throws Exception {
        JSONArray profiles = readData().getJSONArray("profiles");
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.getJSONObject(index);
            if (id.equals(profile.optString("id"))) {
                try {
                    return new JSONObject()
                            .put("name", profile.optString("name"))
                            .put("host", profile.getString("host"))
                            .put("port", profile.getInt("port"))
                            .put("username", profile.getString("username"))
                            .put("password", PasswordCipher.decrypt(profile))
                            .put("initialPath", profile.optString("initialPath", "/"));
                } catch (Exception exception) {
                    throw new IllegalArgumentException("保存的密码无法解密，请编辑服务器并重新输入密码");
                }
            }
        }
        throw new IllegalArgumentException("服务器配置不存在，可能已被删除");
    }

    synchronized void touch(String id) throws JSONException {
        JSONObject data = readData();
        JSONArray profiles = data.getJSONArray("profiles");
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.getJSONObject(index);
            if (id.equals(profile.optString("id"))) {
                profile.put("lastUsedAt", System.currentTimeMillis());
                writeData(data);
                return;
            }
        }
    }

    synchronized JSONObject remove(String id) throws JSONException {
        JSONObject data = readData();
        JSONArray profiles = data.getJSONArray("profiles");
        JSONArray remaining = new JSONArray();
        boolean found = false;
        for (int index = 0; index < profiles.length(); index++) {
            JSONObject profile = profiles.getJSONObject(index);
            if (id.equals(profile.optString("id"))) found = true;
            else remaining.put(profile);
        }
        if (!found) {
            throw new IllegalArgumentException("服务器配置不存在，可能已被删除");
        }
        data.put("profiles", remaining);
        writeData(data);
        return new JSONObject().put("success", true);
    }
}
