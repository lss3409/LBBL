package org.levimc.launcher.filemanager.ui;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

import org.levimc.launcher.ui.activities.BaseActivity;

/**
 * v0.4.104/105：图片 OCR 文字识别（Tesseract 本地引擎，模型随 APK
 * 打包——tessdata_fast chi_sim+eng，无网络依赖）。
 * v0.4.105：ML Kit（模型需 GMS 动态下载）换 tess-two 本地模型。
 */
public final class ImageOcrHelper {

    private static final String DATA_DIR = "tesseract";
    private static final String[] MODELS = {"chi_sim.traineddata", "eng.traineddata"};
    private static final String LANG = "chi_sim+eng";

    private static com.googlecode.tesseract.android.TessBaseAPI api;
    private static volatile boolean busy = false;

    private ImageOcrHelper() {}

    /** 首次初始化：assets 模型拷到私有目录 + 加载引擎（后台线程）。 */
    private static synchronized void ensureInit(Context ctx) {
        if (api != null) {
            return;
        }
        try {
            java.io.File tessDir = new java.io.File(ctx.getFilesDir(), DATA_DIR);
            java.io.File modelDir = new java.io.File(tessDir, "tessdata");
            if (!modelDir.exists()) {
                modelDir.mkdirs();
            }
            for (String m : MODELS) {
                java.io.File dst = new java.io.File(modelDir, m);
                if (!dst.exists()) {
                    try (java.io.InputStream in = ctx.getAssets().open("tessdata/" + m);
                         java.io.OutputStream out = new java.io.FileOutputStream(dst)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                }
            }
            com.googlecode.tesseract.android.TessBaseAPI a =
                    new com.googlecode.tesseract.android.TessBaseAPI();
            if (a.init(tessDir.getAbsolutePath(), LANG)) {
                api = a;
            } else {
                a.end();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 识别位图文字（本地模型，后台线程）。onDone 在主线程回调。 */
    public static void recognize(BaseActivity activity, Bitmap bitmap,
                                 @Nullable java.util.function.Consumer<String> onDone) {
        if (busy || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        busy = true;
        new Thread(() -> {
            // lambda effectively final：识别结果抽静态方法返回
            final String result = runRecognition(activity, bitmap);
            busy = false;
            activity.runOnUiThread(() -> {
                if (onDone != null) {
                    onDone.accept(result);
                }
            });
        }, "fm-ocr").start();
    }

    /** 后台识别（可能耗时数秒），失败返回 null。 */
    private static String runRecognition(Context activity, Bitmap bitmap) {
        try {
            ensureInit(activity);
            if (api != null) {
                api.setImage(bitmap);
                String text = api.getUTF8Text();
                api.clear();
                return text;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 释放引擎（文件管理器退出时调用）。 */
    public static void release() {
        busy = false;
        if (api != null) {
            try {
                api.end();
            } catch (Throwable ignored) {
            }
            api = null;
        }
    }
}
