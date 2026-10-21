package org.levimc.launcher.filemanager.ui;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * v0.4.104：图片 OCR 文字识别（ML Kit 中英混合，on-device）。
 * 单例识别器 + 防重入；识别结果按块/行拼接文本。
 */
public final class ImageOcrHelper {

    private static volatile com.google.mlkit.vision.text.TextRecognizer recognizer;
    private static volatile boolean busy = false;

    private ImageOcrHelper() {}

    /**
     * 识别位图文字（中英混合）。onDone 在主线程回调：成功传文本、
     * 失败传 null；忙碌中（上一次未完成）直接忽略。
     */
    public static void recognize(BaseActivity activity, Bitmap bitmap,
                                 @Nullable java.util.function.Consumer<String> onDone) {
        if (busy || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        busy = true;
        if (recognizer == null) {
            synchronized (ImageOcrHelper.class) {
                if (recognizer == null) {
                    recognizer = com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
                            .Builder().build();
                }
            }
        }
        com.google.mlkit.vision.common.InputImage image =
                com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0);
        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    busy = false;
                    StringBuilder sb = new StringBuilder();
                    for (com.google.mlkit.vision.text.Text.TextBlock block : result.getTextBlocks()) {
                        for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                            if (sb.length() > 0) {
                                sb.append('\n');
                            }
                            sb.append(line.getText());
                        }
                    }
                    String text = sb.toString();
                    activity.runOnUiThread(() -> {
                        if (onDone != null) {
                            onDone.accept(text);
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    busy = false;
                    activity.runOnUiThread(() -> {
                        if (onDone != null) {
                            onDone.accept(null);
                        }
                    });
                });
    }

    /** 释放识别器（模型驻留内存较大，文件管理器退出时调用）。 */
    public static void release() {
        busy = false;
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Throwable ignored) {
            }
            recognizer = null;
        }
    }
}
