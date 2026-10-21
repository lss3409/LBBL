package org.levimc.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * v0.4.132：谷歌风波浪加载进度条（Splash「Preparing to launch」）。
 * 两条错相位正弦波叠加成水面起伏，随进度填充；颜色支持个性化
 * 强调色（铁律：UI 兼容个性化强调色）。
 */
public class WaveProgressView extends View {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();

    private float progress = 0f;      // 0..1
    private float phase = 0f;         // 波浪相位 0..1
    private int waveColor = 0xFF4CAF50;
    private ValueAnimator phaseAnimator;

    public WaveProgressView(Context context) {
        super(context);
        init();
    }

    public WaveProgressView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(dp(1f));
        highlightPaint.setColor(Color.argb(90, 255, 255, 255));
        trackPaint.setColor(Color.argb(26, 0, 0, 0));
    }

    /** 个性化强调色（MaterialButton/AccentStyler 同源 accent）。 */
    public void setWaveColor(int color) {
        waveColor = color;
        invalidate();
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    /** 开始波浪起伏动画（无限循环）。 */
    public void startWaving() {
        if (phaseAnimator != null) {
            return;
        }
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f);
        phaseAnimator.setDuration(1400);
        phaseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        phaseAnimator.setInterpolator(new LinearInterpolator());
        phaseAnimator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        phaseAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (phaseAnimator != null) {
            phaseAnimator.cancel();
            phaseAnimator = null;
        }
    }

    private float waveAt(float x, float waveLength, float amplitude) {
        double k = Math.PI * 2 / waveLength;
        double a1 = Math.sin(k * x + phase * Math.PI * 2) * amplitude;
        double a2 = Math.sin(k * x * 1.7 - phase * Math.PI * 3.2) * amplitude * 0.55;
        return (float) (Math.max(a1, a2) * 0.75);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float filledW = w * progress;
        float baseY = h * 0.5f;
        float amplitude = Math.max(2f, h * 0.22f);
        float waveLength = Math.max(24f, w * 0.3f);

        // 轨道底（进度未覆盖区域，微透明黑槽）
        trackPaint.setColor(Color.argb(isNightMode() ? 60 : 30, 0, 0, 0));
        canvas.drawRoundRect(0f, baseY - amplitude, w, baseY + amplitude,
                h / 2f, h / 2f, trackPaint);

        if (filledW <= 0) {
            return;
        }
        // 波浪填充路径：上边缘 = 波浪线，下边缘 = 槽底
        wavePath.reset();
        wavePath.moveTo(0f, h);
        wavePath.lineTo(0f, baseY + waveAt(0f, waveLength, amplitude));
        for (float x = 4f; x <= filledW + 4f; x += 4f) {
            wavePath.lineTo(x, baseY + waveAt(x, waveLength, amplitude));
        }
        wavePath.lineTo(filledW, h);
        wavePath.close();
        fillPaint.setColor(waveColor);
        canvas.drawPath(wavePath, fillPaint);
        canvas.drawPath(wavePath, highlightPaint);

        // 进度末端光点（谷歌风小亮点）
        if (filledW < w - 1) {
            fillPaint.setColor(Color.argb(220,
                    Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)));
            canvas.drawCircle(filledW, baseY, dp(1.6f), fillPaint);
        }
    }

    private boolean isNightMode() {
        int mode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private float dp(float v) {
        return Math.max(1f, v * getResources().getDisplayMetrics().density);
    }
}
