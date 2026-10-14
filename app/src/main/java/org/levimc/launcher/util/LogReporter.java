package org.levimc.launcher.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
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

/** v0.4.24：日志上报——收集崩溃日志/调试日志/设备信息打包 zip，
 *  走系统邮件预填发送给开发者（lishushu8888@outlook.com）。
 *  v0.4.28：SMTP 直发放弃（用户拍板），恢复系统邮件；发送前弹窗
 *  可填崩溃描述（写入邮件正文）。 */
public final class LogReporter {

    private static final String REPORT_TO = "lishushu8888@outlook.com";

    private LogReporter() {
    }

    /** 点击上报：弹描述弹窗 → 后台收集 → 拉起系统邮件（预填收件人）。 */
    public static void report(Activity activity) {
        showReportDialog(activity);
    }

    // ---------------- 发送前弹窗（只填崩溃描述） ----------------

    private static void showReportDialog(Activity activity) {
        int onSurface = ContextCompat.getColor(activity, R.color.on_surface);
        int hintColor = ContextCompat.getColor(activity, R.color.text_secondary);
        float density = activity.getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * density);
        root.setPadding(pad, 0, pad, 0);

        EditText descInput = new EditText(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descInput.setLayoutParams(lp);
        descInput.setBackgroundResource(R.drawable.bg_spinner_outline);
        descInput.setPadding(pad, pad, pad, pad);
        descInput.setTextColor(onSurface);
        descInput.setHintTextColor(hintColor);
        descInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        descInput.setHint(activity.getString(R.string.report_crash_desc_hint));
        descInput.setMinLines(3);
        descInput.setGravity(Gravity.TOP);
        descInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(descInput);

        new CustomAlertDialog(activity)
                .setTitleText(activity.getString(R.string.report_logs))
                .setCustomView(root)
                .setPositiveButton(activity.getString(R.string.report_logs), v -> {
                    String desc = descInput.getText().toString().trim();
                    collectAndSend(activity, desc);
                })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    // ---------------- 收集 + 拉起邮件 ----------------

    private static void collectAndSend(Activity activity, String desc) {
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
                if (!sendMail(activity, result, desc)) {
                    Toast.makeText(activity, R.string.report_logs_no_app, Toast.LENGTH_LONG).show();
                }
            });
        }, "log-reporter").start();
    }

    /** 系统邮件预填（收件人+主题+描述正文+zip 附件）。
     *  type 用 message/rfc822：Outlook 才会当邮件草稿并预填收件人
     *  （v0.4.28 修复「点发送跑到草稿里没收件人」）。 */
    private static boolean sendMail(Activity activity, File zip, String desc) {
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
            intent.setType("message/rfc822");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{REPORT_TO});
            intent.putExtra(Intent.EXTRA_SUBJECT, "[LBBL Log Report] v" + version);
            StringBuilder body = new StringBuilder();
            if (desc != null && !desc.isEmpty()) {
                body.append(desc).append("\n\n");
            }
            body.append("（日志由 LBBL 自动收集，见附件 zip）\n");
            body.append("Version: ").append(version).append('\n');
            body.append("Device: ").append(Build.MANUFACTURER).append(' ')
                    .append(Build.MODEL).append('\n');
            body.append("Android: ").append(Build.VERSION.RELEASE)
                    .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
            intent.putExtra(Intent.EXTRA_TEXT, body.toString());
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

    // ---------------- 日志收集 ----------------

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
