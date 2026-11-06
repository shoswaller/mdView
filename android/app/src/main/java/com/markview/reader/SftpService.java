package com.markview.reader;

import android.util.Base64;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.UserInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

final class SftpService {
    private static final long MAX_DOCUMENT_SIZE = 10L * 1024L * 1024L;

    private Session session;
    private ChannelSftp channel;
    private String description = "";

    String handle(String action, String payload) {
        try {
            JSONObject data = new JSONObject(payload == null ? "{}" : payload);
            switch (action) {
                case "connect":
                    return connect(data).toString();
                case "disconnect":
                    close();
                    return new JSONObject().put("success", true).toString();
                case "list":
                    return list(data.getString("path")).toString();
                case "read":
                    return read(data.getString("path")).toString();
                case "write":
                    return write(data.getString("path"), data.getString("source")).toString();
                default:
                    throw new IllegalArgumentException("不支持的 SFTP 操作");
            }
        } catch (Exception exception) {
            try {
                return new JSONObject().put("success", false)
                        .put("message", errorMessage(exception)).toString();
            } catch (Exception ignored) {
                return "{\"success\":false,\"message\":\"SFTP 操作失败\"}";
            }
        }
    }

    private JSONObject connect(JSONObject config) throws Exception {
        String host = config.optString("host").trim();
        String username = config.optString("username").trim();
        String password = config.optString("password");
        int port = config.optInt("port", 22);
        if (host.isEmpty() || host.length() > 253 || host.matches(".*[\\s/\\\\].*")) {
            throw new IllegalArgumentException("请输入有效的主机名或 IP 地址");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须是 1 到 65535 之间的整数");
        }
        if (username.isEmpty() || username.indexOf('\r') >= 0 || username.indexOf('\n') >= 0
                || username.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("请输入有效的用户名");
        }
        if (password.isEmpty()) {
            throw new IllegalArgumentException("请输入服务器密码");
        }

        close();
        JSch jsch = new JSch();
        Session newSession = jsch.getSession(username, host, port);
        newSession.setPassword(password);
        newSession.setConfig("StrictHostKeyChecking", "no");
        newSession.setConfig("PreferredAuthentications", "password,keyboard-interactive");
        newSession.setUserInfo(new UserInfo() {
            @Override public String getPassword() { return password; }
            @Override public boolean promptPassword(String message) { return true; }
            @Override public String getPassphrase() { return null; }
            @Override public boolean promptPassphrase(String message) { return false; }
            @Override public boolean promptYesNo(String message) { return false; }
            @Override public void showMessage(String message) { }
        });
        ChannelSftp newChannel = null;
        try {
            newSession.connect(15000);
            newChannel = (ChannelSftp) newSession.openChannel("sftp");
            newChannel.connect(15000);
        } catch (Exception exception) {
            if (newChannel != null) {
                newChannel.disconnect();
            }
            newSession.disconnect();
            throw exception;
        }
        session = newSession;
        channel = newChannel;
        description = username + "@" + host + ":" + port;
        return new JSONObject().put("success", true).put("description", description);
    }

    private JSONObject list(String remotePath) throws Exception {
        ChannelSftp sftp = requireChannel();
        validatePath(remotePath);
        sftp.cd(remotePath);
        String absolutePath = sftp.pwd();
        Vector<ChannelSftp.LsEntry> remoteEntries = sftp.ls(absolutePath);
        List<ChannelSftp.LsEntry> sorted = new ArrayList<>(remoteEntries);
        Collections.sort(sorted, (left, right) -> {
            boolean leftDirectory = left.getAttrs().isDir();
            boolean rightDirectory = right.getAttrs().isDir();
            if (leftDirectory != rightDirectory) {
                return leftDirectory ? -1 : 1;
            }
            return left.getFilename().compareToIgnoreCase(right.getFilename());
        });
        JSONArray entries = new JSONArray();
        for (ChannelSftp.LsEntry entry : sorted) {
            String name = entry.getFilename();
            SftpATTRS attributes = entry.getAttrs();
            entries.put(new JSONObject()
                    .put("name", name)
                    .put("path", joinPath(absolutePath, name))
                    .put("directory", attributes.isDir())
                    .put("size", attributes.getSize())
                    .put("modified", attributes.getMTime() * 1000L));
        }
        return new JSONObject().put("success", true).put("path", absolutePath).put("entries", entries);
    }

    private JSONObject read(String remotePath) throws Exception {
        ChannelSftp sftp = requireChannel();
        validatePath(remotePath);
        if (!remotePath.matches("(?i).*\\.(md|markdown|mdown|mkd|txt)$")) {
            throw new IllegalArgumentException("请选择 Markdown 或 .txt 文本文件");
        }
        SftpATTRS attributes = sftp.stat(remotePath);
        if (attributes.isDir()) {
            throw new IllegalArgumentException("远程路径是目录，请选择文件");
        }
        if (attributes.getSize() > MAX_DOCUMENT_SIZE) {
            throw new IllegalArgumentException("文件超过 10 MB，请选择较小的文本文件");
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream input = sftp.get(remotePath)) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                buffer.write(chunk, 0, count);
                if (buffer.size() > MAX_DOCUMENT_SIZE) {
                    throw new IllegalArgumentException("文件超过 10 MB，请选择较小的文本文件");
                }
            }
        }
        String name = remotePath.substring(remotePath.lastIndexOf('/') + 1);
        return new JSONObject().put("success", true).put("name", name).put("path", remotePath)
                .put("encoded", Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP));
    }

    private JSONObject write(String remotePath, String source) throws Exception {
        ChannelSftp sftp = requireChannel();
        validatePath(remotePath);
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_DOCUMENT_SIZE) {
            throw new IllegalArgumentException("文件超过 10 MB，无法通过当前窗口保存");
        }
        try (OutputStream output = sftp.put(remotePath, ChannelSftp.OVERWRITE)) {
            output.write(bytes);
            output.flush();
        }
        return new JSONObject().put("success", true).put("path", remotePath);
    }

    private ChannelSftp requireChannel() {
        if (session == null || channel == null || !session.isConnected() || !channel.isConnected()) {
            throw new IllegalStateException("SFTP 连接已断开，请重新连接");
        }
        return channel;
    }

    private static void validatePath(String remotePath) {
        if (remotePath == null || remotePath.isEmpty() || remotePath.length() > 4096
                || remotePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("请输入有效的远程路径");
        }
    }

    private static String joinPath(String directory, String name) {
        return directory.endsWith("/") ? directory + name : directory + "/" + name;
    }

    private static String errorMessage(Exception exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (message.contains("Auth fail") || message.contains("Auth cancel")) {
            return "服务器拒绝了用户名或密码";
        }
        if (message.contains("UnknownHost") || message.contains("Name or service not known")) {
            return "找不到服务器，请检查主机地址";
        }
        if (exception instanceof SocketTimeoutException || message.contains("timeout")) {
            return "连接服务器超时，请检查网络后重试";
        }
        if (message.contains("Connection refused")) {
            return "无法连接服务器，请检查地址、端口和 SFTP 服务";
        }
        return message.isEmpty() ? "SFTP 操作失败，请检查连接和远程路径" : message;
    }

    void close() {
        if (channel != null) {
            channel.disconnect();
            channel = null;
        }
        if (session != null) {
            session.disconnect();
            session = null;
        }
        description = "";
    }
}
