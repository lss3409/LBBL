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
 * v0.4.132/133：心电图式波浪加载进度条（Splash「Preparing to launch」）。
 * 一条水平中线上的规律正弦波（波峰波谷平滑流动），进度=已读部分
 * accent 色高亮，未读部分淡色轨道波；相位无限左流（系统更新界面
 * 同款观感）。颜色支持个性化强调色。
 */
public class WaveProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wavePath = new Path();

    private float progress = 0f;      // 0..1
    private float phase = 0f;         // 波形相位 0..1（左流）
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
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setStrokeJoin(Paint.Join.ROUND);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeJoin(Paint.Join.ROUND);
        glowPaint.setColor(Color.argb(60, 255, 255, 255));
    }

    /** 个性化强调色。 */
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

    /** 波形相位无限左流。 */
    public void startWaving() {
        if (phaseAnimator != null) {
            return;
        }
        phaseAnimator = ValueAnimator.ofFloat(0f, 1f);
        phaseAnimator.setDuration(900);
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

    /** 波形函数：中线上的规律正弦波（顺滑波峰波谷）。 */
    private float waveY(float x, float waveLength, float amplitude, float centerY) {
        double k = Math.PI * 2 / waveLength;
        return centerY + (float) Math.sin(k * x - phase * Math.PI * 2) * amplitude;
    }

    private void buildWave(float fromX, float toX, float waveLength,
                           float amplitude, float centerY) {
        wavePath.reset();
        wavePath.moveTo(fromX, waveY(fromX, waveLength, amplitude, centerY));
        for (float x = fromX + 2f; x <= toX + 2f; x += 2f) {
            wavePath.lineTo(x, waveY(x, waveLength, amplitude, centerY));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float density = getResources().getDisplayMetrics().density;
        float centerY = h / 2f;
        float amplitude = Math.min(h * 0.36f, 12f * density);
        float waveLength = Math.max(28f, 46f * density);
        float strokeW = Math.max(2f, 2.4f * density);

        // 轨道：完整波形淡色（未读部分可见，无长框背景）
        trackPaint.setStrokeWidth(strokeW);
        trackPaint.setColor((waveColor & 0x00FFFFFF) | 0x32000000);
        buildWave(0f, w, waveLength, amplitude, centerY);
        canvas.drawPath(wavePath, trackPaint);

        float filledW = w * progress;
        if (filledW <= 0) {
            return;
        }
        // 已读：accent 波形（clip 到进度位置）
        progressPaint.setStrokeWidth(strokeW);
        progressPaint.setColor(waveColor);
        canvas.save();
        canvas.clipRect(0f, 0f, filledW, h);
        buildWave(-waveLength, w + waveLength, waveLength, amplitude, centerY);
        canvas.drawPath(wavePath, progressPaint);
        canvas.restore();

        // 进度末端光点（带光晕，心电图读数点）
        float dotY = waveY(filledW, waveLength, amplitude, centerY);
        dotPaint.setColor(Color.argb(255,
                Color.red(waveColor), Color.green(waveColor), Color.blue(waveColor)));
        canvas.drawCircle(filledW, dotY, 3f * density, dotPaint);
        glowPaint.setColor((waveColor & 0x00FFFFFF) | 0x50000000);
        canvas.drawCircle(filledW, dotY, 7f * density, glowPaint);
    }
}
