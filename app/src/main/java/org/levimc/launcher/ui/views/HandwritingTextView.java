package org.levimc.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * v0.4.132：LBBL 手写艺术字——苹果风「描边手写」动画：取粗体字形
 * 轮廓路径，PathMeasure 按进度逐段绘制（像手写一样显现），底部
 * 淡色 ghost 引导。颜色支持个性化强调色。
 */
public class HandwritingTextView extends View {

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path glyphPath = new Path();
    private final Path drawPath = new Path();
    private final PathMeasure measure = new PathMeasure();

    private String text = "LBBL";
    private float textSizeSp = 40f;
    private int textColor = 0xFF4CAF50;
    private float hwProgress = 1f; // 1 = 完成（无动画直接显示）
    private ValueAnimator hwAnimator;
    private boolean pathBuilt = false;

    public HandwritingTextView(Context context) {
        super(context);
        init();
    }

    public HandwritingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeWidth(dp(2.4f));
        ghostPaint.setStyle(Paint.Style.STROKE);
        ghostPaint.setStrokeJoin(Paint.Join.ROUND);
        ghostPaint.setStrokeCap(Paint.Cap.ROUND);
        ghostPaint.setStrokeWidth(dp(2.4f));
    }

    public void setText(String t) {
        text = t != null ? t : "";
        pathBuilt = false;
        requestLayout();
        invalidate();
    }

    public void setTextSizeSp(float sp) {
        textSizeSp = sp;
        pathBuilt = false;
        requestLayout();
        invalidate();
    }

    /** 个性化强调色。 */
    public void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    /** 开始手写动画（0→1 逐段描画）。 */
    public void startHandwriting(long durationMs) {
        if (hwAnimator != null) {
            hwAnimator.cancel();
        }
        hwProgress = 0f;
        hwAnimator = ValueAnimator.ofFloat(0f, 1f);
        hwAnimator.setDuration(durationMs);
        hwAnimator.setInterpolator(new DecelerateInterpolator(1.1f));
        hwAnimator.addUpdateListener(a -> {
            hwProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        hwAnimator.start();
    }

    private void buildPathIfNeeded() {
        if (pathBuilt || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        textPaint.setTextSize(sp(textSizeSp));
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        textPaint.setLetterSpacing(0.06f);
        glyphPath.reset();
        float w = textPaint.measureText(text);
        float x = (getWidth() - w) / 2f;
        float baseline = (getHeight() - (textPaint.descent() + textPaint.ascent())) / 2f
                - textPaint.ascent();
        textPaint.getTextPath(text, 0, text.length(), x, baseline, glyphPath);
        measure.setPath(glyphPath, false);
        pathBuilt = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        buildPathIfNeeded();
        if (!pathBuilt) {
            return;
        }
        ghostPaint.setColor((textColor & 0x00FFFFFF) | 0x50000000);
        canvas.drawPath(glyphPath, ghostPaint);
        strokePaint.setColor(textColor);
        if (hwProgress >= 1f) {
            canvas.drawPath(glyphPath, strokePaint);
            return;
        }
        float stop = measure.getLength() * hwProgress;
        drawPath.reset();
        measure.getSegment(0f, stop, drawPath, true);
        canvas.drawPath(drawPath, strokePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (hwAnimator != null) {
            hwAnimator.cancel();
            hwAnimator = null;
        }
    }

    private float dp(float v) {
        return Math.max(1f, v * getResources().getDisplayMetrics().density);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
