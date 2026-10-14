package org.levimc.launcher.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.dialogs.CustomAlertDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** v0.4.24：日志上报。
 *  v0.4.26：SMTP 直发（smtp.qq.com:465 SSL，授权码内置 Base64 混淆，
 *  参考用户密码轮换工具）——真正发送完成才回调 onDone（绿勾提示）；
 *  发送前弹窗可填崩溃描述 + 改发件邮箱/授权码（prefs 记忆）；
 *  失败时提供系统邮件备选。 */
public final class LogReporter {

    private static final String PREFS = "log_reporter";
    private static final String KEY_FROM = "from_email";
    private static final String KEY_AUTH = "auth_code";

    private static final String REPORT_TO = "lishushu8888@outlook.com";
    private static final String SMTP_HOST = "smtp.qq.com";
    private static final int SMTP_PORT = 465;

    private LogReporter() {
    }

    /** 点击上报：先弹描述/发件弹窗 → 后台收集 + SMTP 发送。
     *  onDone：邮件真正发送成功后主线程回调。 */
    public static void report(Activity activity, Runnable onDone) {
        showReportDialog(activity, onDone);
    }

    // ---------------- 发送前弹窗 ----------------

    /** v0.4.26：首次（未配置发件）弹描述+邮箱+授权码；之后只弹描述。
     *  发件邮箱/授权码一律空白让用户自己填（不内置预填）。 */
    private static void showReportDialog(Activity activity, Runnable onDone) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String savedFrom = prefs.getString(KEY_FROM, "");
        String savedAuth = prefs.getString(KEY_AUTH, "");
        final boolean configured = !savedFrom.isEmpty() && !savedAuth.isEmpty();

        int onSurface = ContextCompat.getColor(activity, R.color.on_surface);
        int hintColor = ContextCompat.getColor(activity, R.color.text_secondary);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        root.setPadding(pad, 0, pad, 0);

        EditText descInput = makeEdit(activity, onSurface, hintColor, true);
        descInput.setHint(activity.getString(R.string.report_crash_desc_hint));
        descInput.setMinLines(3);
        root.addView(descInput);

        EditText fromInput = null;
        EditText authInput = null;
        if (!configured) {
            fromInput = makeEdit(activity, onSurface, hintColor, false);
            fromInput.setHint(activity.getString(R.string.report_from_email));
            root.addView(fromInput);

            authInput = makeEdit(activity, onSurface, hintColor, false);
            authInput.setHint(activity.getString(R.string.report_auth_code));
            root.addView(authInput);
        }
        final EditText fFrom = fromInput;
        final EditText fAuth = authInput;

        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_logs))
                .setCustomView(root)
                .setPositiveButton(activity.getString(R.string.report_send_button), v -> {
                    String desc = descInput.getText().toString().trim();
                    String from = configured ? savedFrom
                            : (fFrom != null ? fFrom.getText().toString().trim() : "");
                    String auth = configured ? savedAuth
                            : (fAuth != null ? fAuth.getText().toString().trim() : "");
                    if (from.isEmpty() || auth.isEmpty()) {
                        Toast.makeText(activity, R.string.report_need_account,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    prefs.edit().putString(KEY_FROM, from).putString(KEY_AUTH, auth).apply();
                    collectAndSend(activity, desc, from, auth, onDone);
                })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    private static EditText makeEdit(Context context, int textColor, int hintColor, boolean multiline) {
        EditText et = new EditText(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (8 * context.getResources().getDisplayMetrics().density);
        lp.bottomMargin = margin;
        et.setLayoutParams(lp);
        et.setBackgroundResource(R.drawable.bg_spinner_outline);
        int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
        et.setPadding(pad, pad, pad, pad);
        et.setTextColor(textColor);
        et.setHintTextColor(hintColor);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setSingleLine(!multiline);
        et.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        if (multiline) {
            et.setGravity(Gravity.TOP);
        }
        return et;
    }

    // ---------------- 收集 + 发送 ----------------

    private static void collectAndSend(Activity activity, String desc, String from,
                                       String auth, Runnable onDone) {
        Toast.makeText(activity, R.string.report_logs_collecting, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String error = null;
            File zip = null;
            try {
                zip = collect(activity);
            } catch (Exception e) {
                error = String.valueOf(e.getMessage());
            }
            final File result = zip;
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
                Toast.makeText(activity, R.string.report_sending, Toast.LENGTH_SHORT).show();
                sendViaSmtp(activity, from, auth, desc, result, onDone);
            });
        }, "log-reporter").start();
    }

    /** SMTP 直发；失败弹窗提供系统邮件备选。 */
    private static void sendViaSmtp(Activity activity, String from, String auth,
                                    String desc, File zip, Runnable onDone) {
        String version = "";
        try {
            version = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String subject = "[LBBL Log Report] v" + version;
        StringBuilder body = new StringBuilder();
        if (desc != null && !desc.isEmpty()) {
            body.append("崩溃描述：\n").append(desc).append("\n\n");
        }
        body.append("（日志由 LBBL 自动收集，见附件 zip）\n");
        body.append("Version: ").append(version).append('\n');
        body.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        body.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");

        SmtpSender.send(SMTP_HOST, SMTP_PORT, from, auth, from,
                new String[]{REPORT_TO}, subject, body.toString(), zip,
                new SmtpSender.Callback() {
                    @Override
                    public void onSuccess() {
                        Handler main = new Handler(Looper.getMainLooper());
                        main.post(() -> {
                            Toast.makeText(activity, R.string.report_logs_uploaded,
                                    Toast.LENGTH_SHORT).show();
                            if (onDone != null) {
                                onDone.run();
                            }
                        });
                    }

                    @Override
                    public void onFailure(String error) {
                        Handler main = new Handler(Looper.getMainLooper());
                        main.post(() -> showSendFailed(activity, error, zip));
                    }
                });
    }

    /** 发送失败：提示原因 + 系统邮件备选。 */
    private static void showSendFailed(Activity activity, String error, File zip) {
        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_send_failed_title))
                .setMessage(activity.getString(R.string.report_send_failed, error))
                .setPositiveButton(activity.getString(R.string.report_send_fallback), v -> {
                    if (!sendMail(activity, zip)) {
                        Toast.makeText(activity, R.string.report_logs_no_app, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    /** 系统邮件预填（备选路径；不回调 onDone——无法确认发送完成）。 */
    private static boolean sendMail(Activity activity, File zip) {
        try {
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".fileprovider", zip);
            String version = "";
            try {
                version = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (Exception ignored) {
            }
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{REPORT_TO});
            intent.putExtra(Intent.EXTRA_SUBJECT, "[LBBL Log Report] v" + version);
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(uri);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent,
                    activity.getString(R.string.report_logs_done));
            if (chooser.resolveActivity(activity.getPackageManager()) == null) {
                return false;
            }
            activity.startActivity(chooser);
            return true;
        } catch (Exception e) {
            Toast.makeText(activity,
                    activity.getString(R.string.report_logs_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
            return false;
        }
    }

    // ---------------- 日志收集（同 v0.4.24） ----------------

    /** 收集日志打包 zip（cache/logs/ 下）。没有日志时抛异常。 */
    private static File collect(Context context) throws Exception {
        File dir = new File(context.getCacheDir(), "logs");
        File[] olds = dir.listFiles();
        if (olds != null) {
            for (File f : olds) {
                if (f.getName().startsWith("lbbl-logs-")) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        if (!dir.exists() && !dir.mkdirs()) {
            throw new Exception("mkdir failed");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File zip = new File(dir, "lbbl-logs-" + stamp + ".zip");

        List<File> sources = new ArrayList<>();
        File info = new File(dir, "device-info.txt");
        writeDeviceInfo(context, info);
        sources.add(info);
        File filesDir = context.getFilesDir();
        collectFiles(filesDir, sources, 0);
        File logcat = new File(dir, "logcat.txt");
        if (dumpLogcat(logcat)) {
            sources.add(logcat);
        }
        if (sources.size() <= 1) {
            throw new Exception("No log files");
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            byte[] buf = new byte[8192];
            for (File f : sources) {
                String name = f.equals(info) ? "device-info.txt"
                        : f.equals(logcat) ? "logcat.txt" : relativeName(filesDir, f);
                zos.putNextEntry(new ZipEntry(name));
                try (FileInputStream fis = new FileInputStream(f)) {
                    int n;
                    while ((n = fis.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();
            }
        }
        return zip;
    }

    private static void collectFiles(File dir, List<File> out, int depth) {
        if (depth > 4 || out.size() > 40) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File f : children) {
            if (f.isDirectory()) {
                String n = f.getName().toLowerCase(Locale.US);
                if (n.contains("xcrash") || n.contains("crash") || n.contains("log")) {
                    collectFiles(f, out, depth + 1);
                }
            } else {
                String n = f.getName().toLowerCase(Locale.US);
                if ((n.endsWith(".log") || n.endsWith(".txt")) && f.length() > 0
                        && f.length() < 10 * 1024 * 1024) {
                    out.add(f);
                }
            }
        }
    }

    private static String relativeName(File base, File f) {
        String basePath = base.getAbsolutePath();
        String path = f.getAbsolutePath();
        return path.startsWith(basePath) ? path.substring(basePath.length() + 1) : f.getName();
    }

    private static void writeDeviceInfo(Context context, File out) throws Exception {
        StringBuilder sb = new StringBuilder();
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
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(sb.toString().getBytes("UTF-8"));
        }
    }

    private static boolean dumpLogcat(File out) {
        try {
            Process p = new ProcessBuilder("logcat", "-d", "-t", "500")
                    .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                 FileOutputStream fos = new FileOutputStream(out)) {
                String line;
                int count = 0;
                while ((line = br.readLine()) != null && count < 2000) {
                    fos.write((line + "\n").getBytes("UTF-8"));
                    count++;
                }
            }
            p.destroy();
            return out.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
