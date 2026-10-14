package org.levimc.launcher.util;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;
import org.levimc.launcher.R;
import org.levimc.launcher.ui.dialogs.CustomAlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** v0.4.24：日志上报。
 *  v0.4.29：接入 LogShare.CN 公共 API（https://api.logshare.cn/v1/log，
 *  无需认证、免费开放）——日志合并文本上传，返回分享链接；
 *  失败回退系统邮件预填。 */
public final class LogReporter {

    private static final String REPORT_TO = "lishushu8888@outlook.com";
    private static final String LOGSHARE_URL = "https://api.logshare.cn/v1/log";
    /** 日志文本上限（API 单条最大 10MB，留余量）。 */
    private static final int MAX_TEXT = 8 * 1024 * 1024;

    private LogReporter() {
    }

    /** 点击上报：弹描述弹窗 → 后台收集+上传 → 成功弹分享链接。 */
    public static void report(Activity activity) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_report_logs, null);
        EditText descInput = v.findViewById(R.id.report_desc_input);
        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_logs))
                .setCustomView(v)
                .setPositiveButton(activity.getString(R.string.report_logs), d -> {
                    String desc = descInput.getText().toString().trim();
                    collectAndUpload(activity, desc);
                })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    // ---------------- 收集 + 上传 ----------------

    private static void collectAndUpload(Activity activity, String desc) {
        Toast.makeText(activity, R.string.report_uploading, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            String text = null;
            try {
                text = collectText(activity);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final String result = text;
            final String err = error;
            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                if (result == null) {
                    if (err != null && err.contains("No log files")) {
                        Toast.makeText(activity, R.string.report_logs_empty, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(activity,
                                activity.getString(R.string.report_logs_failed, err), Toast.LENGTH_LONG).show();
                    }
                    return;
                }
                upload(activity, desc, result);
            });
        }, "log-reporter").start();
    }

    /** 上传 logshare（后台线程）。成功弹分享链接弹窗，失败回退邮件。 */
    private static void upload(Activity activity, String desc, String text) {
        new Thread(() -> {
            String url = null;
            String error = null;
            try {
                url = postLogShare(activity, desc, text);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final String link = url;
            final String err = error;
            Handler main = new Handler(Looper.getMainLooper());
            main.post(() -> {
                if (link != null) {
                    showShareLinkDialog(activity, link);
                } else {
                    showUploadFailed(activity, err, text);
                }
            });
        }, "logshare-upload").start();
    }

    /** POST /v1/log（JSON：content+source）。返回分享链接。 */
    private static String postLogShare(Context context, String desc, String text) throws Exception {
        String version = "";
        try {
            version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        StringBuilder content = new StringBuilder(text.length() + 256);
        if (desc != null && !desc.isEmpty()) {
            content.append("崩溃描述：\n").append(desc).append("\n\n");
        }
        content.append(text);

        JSONObject body = new JSONObject();
        body.put("content", content.toString());
        body.put("name", "lbb-logs.txt");
        body.put("source", "lbb/" + version);

        HttpURLConnection conn = (HttpURLConnection) new URL(LOGSHARE_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }
        int code = conn.getResponseCode();
        java.io.InputStream is = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder resp = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    resp.append(line);
                }
            }
        }
        conn.disconnect();
        if (code >= 200 && code < 300) {
            JSONObject obj = new JSONObject(resp.toString());
            if (obj.optBoolean("success", false)) {
                String u = obj.optString("url", "");
                if (!TextUtils.isEmpty(u)) {
                    return u;
                }
                String id = obj.optString("id", "");
                if (!TextUtils.isEmpty(id)) {
                    return "https://logshare.cn/" + id;
                }
            }
            throw new Exception("bad response: " + resp);
        }
        throw new Exception("HTTP " + code + " " + resp);
    }

    // ---------------- 成功/失败弹窗 ----------------

    /** 上传成功：展示分享链接（复制/分享/打开）。 */
    private static void showShareLinkDialog(Activity activity, String link) {
        View v = activity.getLayoutInflater().inflate(R.layout.dialog_share_link, null);
        android.widget.TextView tv = v.findViewById(R.id.share_link_text);
        tv.setText(link);
        tv.setTextColor(accentOf(activity));
        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_upload_success))
                .setCustomView(v)
                .setPositiveButton(activity.getString(R.string.report_copy_link), d -> {
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("logshare", link));
                    Toast.makeText(activity, R.string.copied, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(activity.getString(R.string.report_share_link), d -> {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, link);
                    activity.startActivity(Intent.createChooser(send, null));
                })
                .setNegativeButton(activity.getString(R.string.close), null)
                .show();
    }

    /** 上传失败：提示原因 + 系统邮件备选。 */
    private static void showUploadFailed(Activity activity, String error, String text) {
        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_upload_failed_title))
                .setMessage(activity.getString(R.string.report_upload_failed, error))
                .setPositiveButton(activity.getString(R.string.report_send_email_fallback), v -> {
                    if (!sendMail(activity, text)) {
                        Toast.makeText(activity, R.string.report_logs_no_app, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    /** 系统邮件备选：正文=日志文本（不再附 zip）。 */
    private static boolean sendMail(Activity activity, String text) {
        try {
            String version = "";
            try {
                version = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("message/rfc822");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{REPORT_TO});
            intent.putExtra(Intent.EXTRA_SUBJECT, "[LBBL Log Report] v" + version);
            intent.putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(intent,
                    activity.getString(R.string.report_logs_done));
            if (chooser.resolveActivity(activity.getPackageManager()) == null) {
                return false;
            }
            activity.startActivity(chooser);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int accentOf(Context context) {
        try {
            int c = new org.levimc.launcher.util.PersonalizationManager(context).getAccentColor();
            if (c != 0) {
                return c;
            }
        } catch (Throwable ignored) {
        }
        return ContextCompat.getColor(context, R.color.primary);
    }

    // ---------------- 日志收集（文本合并） ----------------

    /** 收集日志合并为文本（device-info + 各日志文件 + logcat）。 */
    private static String collectText(Context context) throws Exception {
        StringBuilder sb = new StringBuilder();
        // v0.4.30：标准 Minecraft log4j 格式头——LogShare 的 Codex 探测器
        // 靠它识别日志类型，没有这行会显示「unknown 的日志」
        String version = "";
        try {
            version = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        sb.append("[").append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()))
                .append("] [main/INFO]: Log generated by LBBL (Levi Better Bedrock Launcher) v")
                .append(version).append('\n');
        appendDeviceInfo(context, sb);
        File filesDir = context.getFilesDir();
        int fileCount = appendLogFiles(filesDir, sb, 0);
        if (appendLogcat(sb)) {
            fileCount++;
        }
        if (fileCount == 0) {
            throw new Exception("No log files");
        }
        if (sb.length() > MAX_TEXT) {
            return sb.substring(0, MAX_TEXT) + "\n...(truncated)";
        }
        return sb.toString();
    }

    private static void appendDeviceInfo(Context context, StringBuilder sb) {
        sb.append("=== LBBL Log Report ===\n");
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            sb.append("App: ").append(context.getPackageName()).append('\n');
            sb.append("VersionName: ").append(pi.versionName).append('\n');
            sb.append("VersionCode: ").append(pi.versionCode).append('\n');
        } catch (Exception ignored) {
        }
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        sb.append("Locale: ").append(Locale.getDefault()).append('\n');
        sb.append("Time: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append('\n');
        try {
            sb.append("Storage: custom=")
                    .append(LauncherStorage.getCustomStoragePath(context)).append('\n');
            sb.append("ExtendedRoot=")
                    .append(LauncherStorage.getExtendedRootPath(context)).append('\n');
        } catch (Exception ignored) {
        }
        sb.append('\n');
    }

    private static int appendLogFiles(File dir, StringBuilder sb, int depth) {
        if (depth > 4 || sb.length() > MAX_TEXT) {
            return 0;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File f : children) {
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.contains("xcrash") || n.contains("crash") || n.contains("log")) {
                    count += appendLogFiles(f, sb, depth + 1);
                }
            } else {
                String n = f.getName().toLowerCase(Locale.US);
                if ((n.endsWith(".log") || n.endsWith(".txt")) && f.length() > 0
                        && f.length() < 10 * 1024 * 1024) {
                    sb.append("=== File: ").append(f.getName()).append(" ===\n");
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null && sb.length() < MAX_TEXT) {
                            sb.append(line).append('\n');
                        }
                    } catch (Exception ignored) {
                    }
                    sb.append('\n');
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean appendLogcat(StringBuilder sb) {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-t", "500")
                    .redirectErrorStream(true).start();
            StringBuilder part = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 2000) {
                    part.append(line).append('\n');
                    count++;
                }
            }
            p.destroy();
            if (part.length() > 0) {
                sb.append("=== logcat ===\n").append(part).append('\n');
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
