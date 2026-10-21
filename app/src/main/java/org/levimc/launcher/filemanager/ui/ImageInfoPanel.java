package org.levimc.launcher.filemanager.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.activities.BaseActivity;
import org.levimc.launcher.ui.dialogs.CustomAlertDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * v0.4.105：图片信息面板——文件名/路径/分辨率/大小/修改时间/格式
 * （魔数检测）/色彩深度/透明通道/SHA-256 哈希/RGB 直方图/MC 贴图
 * 比例检测（2 的幂方图，不合规红字；pack_icon/world_icon 白名单
 * 豁免）。分区卡片排版（Framecho/galaxy 风格），主题色文字。
 */
public final class ImageInfoPanel {

    /** 解析结果。 */
    static class Info {
        String name = "";
        String path = "";
        String resolution = "-";
        String size = "-";
        String mtime = "-";
        String format = "-";
        String depth = "-";
        boolean hasAlpha = false;
        String hash = "-";
        int[] rh;
        int[] gh;
        int[] bh;
        /** 0=合规 1=不合规 2=白名单豁免 */
        int mcState = 0;
        int mcSize = 0;
    }

    private ImageInfoPanel() {}

    /** 两段式：先「分析中」弹窗，解析完成替换为信息面板。 */
    public static void show(BaseActivity a, File file) {
        CustomAlertDialog loading = new CustomAlertDialog(a)
                .setTitleText(a.getString(R.string.fm_img_info_title))
                .setMessage(a.getString(R.string.fm_img_info_loading))
                .setNegativeButton(a.getString(R.string.close), v -> FmDialogs.dismissLast());
        FmDialogs.trackShow(loading);

        new Thread(() -> {
            Info info = analyze(file);
            a.runOnUiThread(() -> {
                FmDialogs.dismissLast();
                showPanel(a, info);
            });
        }, "fm-img-info").start();
    }

    /** 后台解析全部信息。 */
    private static Info analyze(File file) {
        Info info = new Info();
        info.name = file.getName();
        info.path = file.getAbsolutePath();
        info.size = formatSize(file.length());
        try {
            info.mtime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(file.lastModified()));
        } catch (Throwable ignored) {
        }
        info.format = detectFormat(file);
        // 位深：PNG IHDR bit depth（文件头第 25 字节）；JPEG 恒 8-bit/通道
        info.depth = readDepth(file, info.format);
        // 哈希 SHA-256
        try {
            info.hash = sha256(file);
        } catch (Throwable ignored) {
        }
        // 分辨率/透明/直方图/贴图比例
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (opts.outWidth > 0 && opts.outHeight > 0) {
            info.resolution = opts.outWidth + " × " + opts.outHeight;
            // 贴图比例检测
            String lower = info.name.toLowerCase(Locale.ROOT);
            if (lower.contains("pack_icon") || lower.contains("world_icon")) {
                info.mcState = 2;
            } else if (opts.outWidth == opts.outHeight
                    && opts.outWidth >= 8
                    && (opts.outWidth & (opts.outWidth - 1)) == 0) {
                info.mcState = 0;
                info.mcSize = opts.outWidth;
            } else {
                info.mcState = 1;
            }
            // 全解码：透明通道 + 直方图（大图采样步进，上限 50 万像素样本）
            Bitmap bmp = null;
            try {
                BitmapFactory.Options dec = new BitmapFactory.Options();
                dec.inSampleSize = 1;
                int area = opts.outWidth * opts.outHeight;
                if (area > 4000000) {
                    dec.inSampleSize = 2;
                }
                bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), dec);
                if (bmp != null) {
                    info.hasAlpha = bmp.hasAlpha();
                    buildHistogram(bmp, info);
                }
            } catch (Throwable ignored) {
            } finally {
                if (bmp != null) {
                    bmp.recycle();
                }
            }
        }
        return info;
    }

    /** 直方图（500K 样本封顶，步进采样）。 */
    private static void buildHistogram(Bitmap bmp, Info info) {
        int[] rh = new int[256];
        int[] gh = new int[256];
        int[] bh = new int[256];
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int total = w * h;
        int step = Math.max(1, total / 500000);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = bmp.getPixel(x, y);
                if ((c >>> 24) == 0) {
                    continue; // 全透明不计数
                }
                rh[(c >> 16) & 0xFF]++;
                gh[(c >> 8) & 0xFF]++;
                bh[c & 0xFF]++;
            }
        }
        info.rh = rh;
        info.gh = gh;
        info.bh = bh;
    }

    /** 魔数格式检测（不依赖扩展名）。 */
    private static String detectFormat(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] head = new byte[16];
            int n = in.read(head);
            if (n >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N'
                    && head[3] == 'G') {
                return "PNG";
            }
            if (n >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8
                    && (head[2] & 0xFF) == 0xFF) {
                return "JPEG";
            }
            if (n >= 6 && head[0] == 'G' && head[1] == 'I' && head[2] == 'F') {
                return "GIF";
            }
            if (n >= 2 && head[0] == 'B' && head[1] == 'M') {
                return "BMP";
            }
            if (n >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F'
                    && head[3] == 'F' && head[8] == 'W' && head[9] == 'E'
                    && head[10] == 'B' && head[11] == 'P') {
                return "WEBP";
            }
        } catch (Throwable ignored) {
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "PNG?";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "JPEG?";
        }
        if (name.endsWith(".gif")) {
            return "GIF?";
        }
        if (name.endsWith(".bmp")) {
            return "BMP?";
        }
        if (name.endsWith(".webp")) {
            return "WEBP?";
        }
        return "-";
    }

    /** PNG IHDR 位深（bit depth 在文件头偏移 24）；JPEG 恒 8-bit/通道。 */
    private static String readDepth(File file, String format) {
        if ("JPEG".equals(format) || "JPEG?".equals(format)) {
            return "8-bit";
        }
        if ("PNG".equals(format)) {
            try (InputStream in = new FileInputStream(file)) {
                byte[] head = new byte[26];
                if (in.read(head) >= 26 && head[12] == 'I' && head[13] == 'H'
                        && head[14] == 'D' && head[15] == 'R') {
                    int bitDepth = head[24] & 0xFF;
                    int colorType = head[25] & 0xFF;
                    int channels = switch (colorType) {
                        case 2 -> 3;
                        case 4 -> 2;
                        case 6 -> 4;
                        default -> 1;
                    };
                    return (bitDepth * channels) + "-bit";
                }
            } catch (Throwable ignored) {
            }
        }
        return "-";
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f));
    }

    /** 信息面板弹窗（分区卡片）。 */
    private static void showPanel(BaseActivity a, Info info) {
        float density = a.getResources().getDisplayMetrics().density;
        int pad = (int) (14 * density);
        int small = (int) (6 * density);
        int onSurface = a.getResources().getColor(R.color.on_surface, a.getTheme());
        int secondary = a.getResources().getColor(R.color.text_secondary, a.getTheme());
        int warn = 0xFFE53935;
        int ok = 0xFF4CAF50;

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, small, pad, small);

        // 卡片 1：基本信息
        root.addView(card(a, a.getString(R.string.fm_img_info_basic),
                row(a, R.string.fm_img_info_name, info.name, onSurface, secondary, density),
                row(a, R.string.fm_img_info_res, info.resolution, onSurface, secondary, density),
                row(a, R.string.fm_img_info_size, info.size, onSurface, secondary, density),
                row(a, R.string.fm_img_info_mtime, info.mtime, onSurface, secondary, density)));

        // 卡片 2：格式信息
        root.addView(card(a, a.getString(R.string.fm_img_info_format),
                row(a, R.string.fm_img_info_fmt, info.format, onSurface, secondary, density),
                row(a, R.string.fm_img_info_depth, info.depth, onSurface, secondary, density),
                row(a, R.string.fm_img_info_alpha,
                        info.hasAlpha ? a.getString(R.string.fm_img_alpha_yes)
                                : a.getString(R.string.fm_img_alpha_no),
                        onSurface, secondary, density)));

        // 卡片 3：RGB 直方图
        if (info.rh != null) {
            RgbHistogramView hist = new RgbHistogramView(a);
            FrameLayout histBox = new FrameLayout(a);
            histBox.addView(hist, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, (int) (110 * density)));
            root.addView(card(a, a.getString(R.string.fm_img_info_histogram), histBox));
        }

        // 卡片 4：哈希值
        root.addView(card(a, a.getString(R.string.fm_img_info_hash),
                row(a, R.string.fm_img_info_sha256, info.hash, onSurface, secondary, density)));

        // 卡片 5：MC 贴图比例检测
        TextView mcText = new TextView(a);
        mcText.setTextSize(13);
        mcText.setLineSpacing(2 * density, 1f);
        if (info.mcState == 2) {
            mcText.setText(a.getString(R.string.fm_img_mc_skip));
            mcText.setTextColor(secondary);
        } else if (info.mcState == 0) {
            mcText.setText(a.getString(R.string.fm_img_mc_ok, info.mcSize));
            mcText.setTextColor(ok);
        } else {
            mcText.setText(a.getString(R.string.fm_img_mc_bad));
            mcText.setTextColor(warn);
        }
        root.addView(card(a, a.getString(R.string.fm_img_info_mc), mcText));

        // 路径单独卡片（完整路径可换行）
        TextView pathText = new TextView(a);
        pathText.setText(info.path);
        pathText.setTextSize(11);
        pathText.setTextColor(secondary);
        pathText.setLineSpacing(2 * density, 1f);
        root.addView(card(a, a.getString(R.string.fm_img_info_path), pathText));

        ScrollView sv = new ScrollView(a);
        sv.addView(root);
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        FrameLayout fl = new FrameLayout(a);
        fl.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, maxH));

        CustomAlertDialog dialog = new CustomAlertDialog(a)
                .setTitleText(a.getString(R.string.fm_img_info_title) + " · " + info.name)
                .setCustomView(fl)
                .setNegativeButton(a.getString(R.string.close), v -> FmDialogs.dismissLast());
        FmDialogs.trackShow(dialog);
    }

    /** 分区卡片容器（圆角背景 + 内边距）。 */
    private static LinearLayout card(BaseActivity a, String title, android.view.View... children) {
        float density = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (12 * density), (int) (10 * density),
                (int) (12 * density), (int) (10 * density));
        box.setBackgroundResource(R.drawable.bg_rounded_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * density);
        box.setLayoutParams(lp);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(12);
        t.setTextColor(a.getResources().getColor(R.color.primary, a.getTheme()));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(t);
        for (android.view.View c : children) {
            if (c != null) {
                box.addView(c);
            }
        }
        return box;
    }

    /** 标签+值行。 */
    private static LinearLayout row(BaseActivity a, int labelRes, String value,
                                    int valueColor, int labelColor, float density) {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setPadding(0, (int) (3 * density), 0, (int) (3 * density));
        TextView label = new TextView(a);
        label.setText(labelRes);
        label.setTextSize(12);
        label.setTextColor(labelColor);
        label.setMinWidth((int) (88 * density));
        TextView val = new TextView(a);
        val.setText(value);
        val.setTextSize(12);
        val.setTextColor(valueColor);
        val.setLineSpacing(1 * density, 1f);
        line.addView(label);
        line.addView(val, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return line;
    }
}
