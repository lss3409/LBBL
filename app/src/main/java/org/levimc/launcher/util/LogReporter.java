package org.levimc.launcher.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.levimc.launcher.R;

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
 *  隐私：只收集启动器自己的文件与 logcat 里本进程的日志。 */
public final class LogReporter {

    private static final String REPORT_EMAIL = "lishushu8888@outlook.com";

    private LogReporter() {
    }

    /** 点击上报：后台线程收集 → 主线程拉起邮件。 */
    public static void report(Activity activity) {
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
                sendMail(activity, result);
            });
        }, "log-reporter").start();
    }

    /** 收集日志打包 zip（cache/logs/ 下）。没有日志时抛异常。 */
    private static File collect(Context context) throws Exception {
        File dir = new File(context.getCacheDir(), "logs");
        // 旧上报包清掉，防止越积越多
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
        // 1. 设备/版本信息
        File info = new File(dir, "device-info.txt");
        writeDeviceInfo(context, info);
        sources.add(info);
        // 2. 应用 files 下的日志（OnlineDebugLog、CrashReporter、xCrash 等）
        File filesDir = context.getFilesDir();
        collectFiles(filesDir, sources, 0);
        // 3. logcat（本进程可见部分）
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

    /** 递归收集 files 下日志类文件（*.log、*.txt 与 xCrash 崩溃目录），
     *  单文件上限 10MB、总文件数上限 40，防止超大日志拖垮邮件。 */
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

    /** logcat -d 导出（只能抓到本进程日志，Android 4.1+ 限制）。 */
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

    /** 邮件预填发送（附件走 FileProvider）。 */
    private static void sendMail(Activity activity, File zip) {
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
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{REPORT_EMAIL});
            intent.putExtra(Intent.EXTRA_SUBJECT, "[LBBL Log Report] v" + version);
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(uri);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(intent,
                    activity.getString(R.string.report_logs_done));
            if (chooser.resolveActivity(activity.getPackageManager()) == null) {
                Toast.makeText(activity, R.string.report_logs_no_app, Toast.LENGTH_LONG).show();
                return;
            }
            activity.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(activity,
                    activity.getString(R.string.report_logs_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }
}
