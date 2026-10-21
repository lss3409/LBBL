package org.levimc.launcher.filemanager.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * v0.4.105：RGB 直方图视图（图片信息面板）——三通道 256 桶曲线，
 * 半透明渐变填充（Framecho/galaxy 卡片式数据可视化风格），
 * 通道色固定语义（R/G/B），网格线用主题次级色。
 */
public class RgbHistogramView extends View {

    private int[] rHist;
    private int[] gHist;
    private int[] bHist;
    private int maxCount = 1;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public RgbHistogramView(Context context) {
        this(context, null);
    }

    public RgbHistogramView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dp(0.5f));
        gridPaint.setColor(0x1F8A8A8A);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(1.4f));
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** 设置直方图数据（主线程调用）。 */
    public void setHistogram(int[] r, int[] g, int[] b) {
        rHist = r;
        gHist = g;
        bHist = b;
        maxCount = 1;
        for (int i = 0; i < 256; i++) {
            maxCount = Math.max(maxCount, Math.max(r[i], Math.max(g[i], b[i])));
        }
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0 || rHist == null) {
            return;
        }
        // 横向网格 3 条
        for (int i = 1; i <= 3; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, gridPaint);
        }
        float base = h;
        drawChannel(canvas, bHist, 0xFF4C8DFF, w, base); // 蓝
        drawChannel(canvas, gHist, 0xFF4CAF50, w, base); // 绿
        drawChannel(canvas, rHist, 0xFFE53935, w, base); // 红
    }

    private void drawChannel(Canvas canvas, int[] hist, int color, float w, float base) {
        float h = getHeight();
        path.reset();
        for (int i = 0; i < 256; i++) {
            float x = w * i / 255f;
            float y = base - h * hist[i] / maxCount;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        // 填充：曲线 → 底部闭合
        fillPaint.setColor((color & 0x00FFFFFF) | 0x26000000);
        Path fill = new Path(path);
        fill.lineTo(w, base);
        fill.lineTo(0, base);
        fill.close();
        canvas.drawPath(fill, fillPaint);
        // 曲线
        linePaint.setColor(color);
        canvas.drawPath(path, linePaint);
    }
}
