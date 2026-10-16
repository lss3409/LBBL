package org.levimc.launcher.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import android.widget.Toast;

import org.levimc.launcher.R;
import org.levimc.launcher.ui.dialogs.CustomAlertDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextEditorActivity extends BaseActivity {

    public static final String EXTRA_PATH = "path";

    private File file;
    private TextView previewText;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_editor);

        String path = getIntent().getStringExtra(EXTRA_PATH);
        file = path != null ? new File(path) : null;

        TextView titleText = findViewById(R.id.text_title);
        previewText = findViewById(R.id.text_preview);

        if (file != null) {
            titleText.setText(file.getName());
        }

        findViewById(R.id.text_back).setOnClickListener(v -> finish());
        findViewById(R.id.text_close).setOnClickListener(v -> finish());
        findViewById(R.id.text_external_button).setOnClickListener(v -> {
            if (file != null) openInMt(file);
        });

        loadContent();
    }

    @Override
    protected boolean shouldSkipNavBar() {
        return true;
    }

    private void loadContent() {
        if (file == null || !file.exists()) {
            previewText.setText(getString(R.string.file_not_found));
            return;
        }
        // v0.4.74：上限放宽到 1MB（256KB 对 .log 等常见文本太小）
        if (file.length() > 1024 * 1024) {
            previewText.setText(getString(R.string.file_too_large));
            return;
        }
        executor.execute(() -> {
            String text = readFile();
            // v0.4.74：显示截断到 300KB 字符（TextView 渲染 1MB 文本会卡）
            int maxChars = 300 * 1024;
            if (text.length() > maxChars) {
                text = text.substring(0, maxChars) + "\n…";
            }
            if (isJsonFile()) {
                SpannableString highlighted = highlightJson(text);
                mainHandler.post(() -> previewText.setText(highlighted));
            } else {
                mainHandler.post(() -> previewText.setText(text));
            }
        });
    }

    /** v0.4.74：循环读满 + 智能编码（UTF-8 严格 → BOM/GBK/Latin-1 兜底）。
     *  此前固定 UTF-8 解码——GBK/ANSI 中文文件全是替换符，看起来"不显示文本"。 */
    private String readFile() {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) Math.min(file.length(), 1024 * 1024)];
            int off = 0;
            while (off < data.length) {
                int read = fis.read(data, off, data.length - off);
                if (read <= 0) {
                    break;
                }
                off += read;
            }
            if (off < data.length) {
                data = java.util.Arrays.copyOf(data, off);
            }
            return decodeSmart(data);
        } catch (IOException e) {
            return "";
        }
    }

    /** BOM → UTF-8 严格 → GBK → Latin-1。 */
    private static String decodeSmart(byte[] data) {
        // UTF-8 BOM
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        // UTF-16 BOM
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
        }
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
            return new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
        }
        // UTF-8 严格解码（失败=非 UTF-8）
        try {
            java.nio.charset.CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return utf8.decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (Exception ignored) {
        }
        // GBK（中文用户最常见的旧编码）
        try {
            return new String(data, java.nio.charset.Charset.forName("GBK"));
        } catch (Exception ignored) {
        }
        return new String(data, StandardCharsets.ISO_8859_1);
    }

    private boolean isJsonFile() {
        return file != null && file.getName().toLowerCase().endsWith(".json");
    }

    private SpannableString highlightJson(String text) {
        SpannableString spannable = new SpannableString(text);
        int blue = 0xFF2196F3;
        int green = 0xFF4CAF50;
        int orange = 0xFFFF9800;
        int purple = 0xFF9C27B0;

        applyPattern(spannable, text, Pattern.compile("\"([^\"\\\\]|\\\\.)*\""), green);
        applyPattern(spannable, text, Pattern.compile("-?\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b"), orange);
        applyPattern(spannable, text, Pattern.compile("\\b(true|false|null)\\b"), purple);
        applyPattern(spannable, text, Pattern.compile("\"[^\"]+\"\\s*:"), blue);

        return spannable;
    }

    private void applyPattern(SpannableString spannable, String text, Pattern pattern, int color) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            spannable.setSpan(new ForegroundColorSpan(color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void openInMt(File file) {
        try {
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            intent.setPackage("bin.mt.plus");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.no_file_manager, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
