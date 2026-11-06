package com.markview.reader;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.View;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_OPEN_FILE = 1001;
    private static final int REQUEST_EXPORT_FILE = 1002;
    private static final String HOME_URL = "file:///android_asset/index.html";

    private final ExecutorService fileExecutor = Executors.newSingleThreadExecutor();
    private final SftpService sftpService = new SftpService();
    private SftpProfileStore sftpProfileStore;
    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingExportName;
    private String pendingExportSource;
    private boolean destroyed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sftpProfileStore = new SftpProfileStore(this);
        updateSystemBars(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(246, 246, 245));
        FrameLayout root = new FrameLayout(this);
        root.setFitsSystemWindows(true);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "text/markdown", "text/plain", "application/octet-stream"
                });
                try {
                    startActivityForResult(Intent.createChooser(intent, "打开 Markdown 文件"),
                            REQUEST_OPEN_FILE);
                    return true;
                } catch (ActivityNotFoundException exception) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "没有可用的文件选择器", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(Uri.parse(url));
            }
        });

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
    }

    private boolean handleNavigation(Uri uri) {
        String url = uri.toString();
        if (url.startsWith(HOME_URL)) {
            return false;
        }
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException exception) {
                Toast.makeText(this, "没有可打开此链接的应用", Toast.LENGTH_LONG).show();
            }
        }
        return true;
    }

    private void updateSystemBars(boolean dark) {
        Window window = getWindow();
        int color = dark ? Color.rgb(20, 20, 20) : Color.rgb(246, 246, 245);
        window.getDecorView().setBackgroundColor(color);
        window.setStatusBarColor(color);
        window.setNavigationBarColor(color);
        int flags = window.getDecorView().getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private void launchOpenDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "text/markdown", "text/plain", "application/octet-stream"
        });
        try {
            startActivityForResult(Intent.createChooser(intent, "打开 Markdown 文件"), REQUEST_OPEN_FILE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "没有可用的文件选择器", Toast.LENGTH_LONG).show();
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        }
        return "打开的文档.md";
    }

    private void readOpenedDocument(Uri uri) {
        fileExecutor.execute(() -> {
            String encoded = null;
            String name = null;
            String error = null;
            try {
                name = displayName(uri);
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        throw new IllegalStateException("Cannot open source");
                    }
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = input.read(chunk)) != -1) {
                        buffer.write(chunk, 0, count);
                    }
                }
                encoded = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
            } catch (Exception exception) {
                error = "无法读取所选文件，请重试";
            }
            String finalEncoded = encoded;
            String finalName = name;
            String finalError = error;
            runOnUiThread(() -> {
                if (!destroyed && webView != null) {
                    String script = "if(window.MarkviewAndroid){window.MarkviewAndroid.openFinished("
                            + JSONObject.quote(finalEncoded == null ? "" : finalEncoded) + ","
                            + JSONObject.quote(finalName == null ? "打开的文档.md" : finalName) + ","
                            + JSONObject.quote(finalError == null ? "" : finalError) + ");}";
                    webView.evaluateJavascript(script, null);
                }
            });
        });
    }

    private void launchExport(String fileName, String source) {
        pendingExportName = fileName;
        pendingExportSource = source;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/markdown");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            startActivityForResult(intent, REQUEST_EXPORT_FILE);
        } catch (ActivityNotFoundException exception) {
            completeExport(false, fileName, source, "没有可用的文件保存应用");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OPEN_FILE) {
            ValueCallback<Uri[]> callback = filePathCallback;
            filePathCallback = null;
            if (callback != null) {
                Uri[] result = resultCode == RESULT_OK
                        ? WebChromeClient.FileChooserParams.parseResult(resultCode, data) : null;
                callback.onReceiveValue(result);
            } else if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                readOpenedDocument(data.getData());
            }
            return;
        }
        if (requestCode != REQUEST_EXPORT_FILE) {
            return;
        }

        String name = pendingExportName;
        String source = pendingExportSource;
        pendingExportName = null;
        pendingExportSource = null;
        Uri destination = resultCode == RESULT_OK && data != null ? data.getData() : null;
        if (destination == null || source == null) {
            completeExport(false, name, source, "已取消导出，修改仍保留在草稿中");
            return;
        }

        fileExecutor.execute(() -> {
            String error = null;
            try (OutputStream output = getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) {
                    throw new IllegalStateException("Cannot open destination");
                }
                output.write(source.getBytes(StandardCharsets.UTF_8));
                output.flush();
            } catch (Exception exception) {
                error = "导出失败，请重试或选择其他位置";
            }
            String finalError = error;
            runOnUiThread(() -> {
                if (!destroyed) {
                    completeExport(finalError == null, name, source, finalError);
                }
            });
        });
    }

    private void completeExport(boolean success, String name, String source, String message) {
        if (webView == null) {
            return;
        }
        String script = "if(window.MarkviewAndroid){window.MarkviewAndroid.exportFinished("
                + success + "," + JSONObject.quote(name == null ? "未命名.md" : name) + ","
                + JSONObject.quote(source == null ? "" : source) + ","
                + JSONObject.quote(message == null ? "" : message) + ");}";
        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    private String handleSftpRequest(String action, String payload) {
        try {
            JSONObject data = new JSONObject(payload == null ? "{}" : payload);
            switch (action) {
                case "profileList":
                    return sftpProfileStore.list().toString();
                case "updateProfile": {
                    JSONObject saved = sftpProfileStore.update(data);
                    return new JSONObject().put("success", true)
                            .put("profile", saved.getJSONObject("profile")).toString();
                }
                case "deleteProfile":
                    return sftpProfileStore.remove(data.optString("id")).toString();
                case "connectSaved": {
                    String id = data.optString("id");
                    JSONObject config = sftpProfileStore.connectionConfig(id);
                    JSONObject result = new JSONObject(sftpService.handle("connect", config.toString()));
                    if (result.optBoolean("success")) {
                        result.put("initialPath", config.optString("initialPath", "/"));
                        result.put("profileId", id);
                        try {
                            sftpProfileStore.touch(id);
                        } catch (Exception exception) {
                            result.put("warning", "服务器已连接，但最近使用时间未能保存");
                        }
                    }
                    return result.toString();
                }
                case "connect": {
                    JSONObject result = new JSONObject(sftpService.handle("connect", data.toString()));
                    if (result.optBoolean("success")) {
                        try {
                            JSONObject saved = sftpProfileStore.save(data, true);
                            JSONObject profile = saved.getJSONObject("profile");
                            result.put("profileId", profile.getString("id"));
                            result.put("initialPath", profile.optString("initialPath", "/"));
                            if (!saved.optBoolean("passwordSaved")) {
                                result.put("warning", "服务器已连接，但系统加密不可用，密码未保存");
                            }
                        } catch (Exception exception) {
                            result.put("warning", "服务器已连接，但配置保存失败，请检查本地存储");
                        }
                    }
                    return result.toString();
                }
                default:
                    return sftpService.handle(action, data.toString());
            }
        } catch (Exception exception) {
            try {
                String message = exception instanceof IllegalArgumentException
                        ? exception.getMessage() : "SFTP 服务器配置操作失败";
                return new JSONObject().put("success", false).put("message", message).toString();
            } catch (Exception ignored) {
                return "{\"success\":false,\"message\":\"SFTP 服务器配置操作失败\"}";
            }
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        fileExecutor.shutdown();
        sftpService.close();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class AndroidBridge {
        @JavascriptInterface
        public void openDocument() {
            runOnUiThread(MainActivity.this::launchOpenDocument);
        }

        @JavascriptInterface
        public void saveDocument(String fileName, String source) {
            if (fileName == null || source == null) {
                return;
            }
            runOnUiThread(() -> launchExport(fileName, source));
        }

        @JavascriptInterface
        public void sftpRequest(int requestId, String action, String payload) {
            if (destroyed) {
                return;
            }
            fileExecutor.execute(() -> {
                String result = handleSftpRequest(action, payload);
                runOnUiThread(() -> {
                    if (!destroyed && webView != null) {
                        String script = "if(window.MarkviewAndroid){window.MarkviewAndroid.sftpFinished("
                                + requestId + "," + JSONObject.quote(result) + ");}";
                        webView.evaluateJavascript(script, null);
                    }
                });
            });
        }

        @JavascriptInterface
        public void applyTheme(String theme) {
            boolean dark = "dark".equals(theme);
            runOnUiThread(() -> updateSystemBars(dark));
        }
    }
}
