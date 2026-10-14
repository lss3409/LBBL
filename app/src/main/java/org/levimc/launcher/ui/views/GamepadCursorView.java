package org.levimc.launcher.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.core.graphics.PathParser;

/**
 * v0.4.11：手柄虚拟光标——箭头 + 描边 + shadowLayer 自发光。
 * v0.4.15：①放大（箭头 30×16.9dp，View 60×60）；②自适应背景色——
 * 背景亮 → 黑箭头白描边+白光晕；背景暗 → 白箭头黑描边（经典
 * 高对比光标方案，参考主流手柄 UI）。pathData 来自用户提供的
 * SVG（gamepad_cursor.xml 同源，viewport 1820×1024）。
 * 尖端锚点：(15+5.9, 15+1.1)dp（View 60×60，箭头内嵌 (15,15)）。
 */
public class GamepadCursorView extends View {

    private static final String PATH_OUTLINE =
            "M688.92444406 1004.562963c-23.798519 0 -46.364444 -14.127407 -56.035555 -37.451852l-103.348148 -242.441481 -87.608889 92.918518c-11.567407 12.136296 -27.496296 19.057778 -44.278519 18.773333 -33.374815 -0.284444 -60.302222 -27.591111 -60.017777 -60.965925V24.177778L870.21037006 542.340741c11.851852 11.282963 18.773333 27.401481 18.583704 44.088889 -0.284444 33.185185 -27.306667 60.017778 -60.491852 60.017777h-117.949629L814.45925906 893.060741c6.068148 14.601481 5.973333 31.099259 -0.189629 45.985185 -6.257778 14.885926 -17.825185 26.548148 -32.805926 32.711111l-69.594074 28.254815c-7.49037 3.034074 -15.265185 4.551111 -22.945186 4.551111z m-146.773333 -349.866667l126.672593 297.33926c4.645926 11.188148 17.256296 16.497778 28.254815 11.946666l69.594074 -28.254815c5.214815 -2.180741 9.386667 -6.257778 11.662222 -11.662222 2.180741 -5.30963 2.275556 -11.188148 0 -16.497778l-126.577778 -299.994074h176.355556c5.499259 0 11.188148 -2.180741 15.36 -6.257777s6.352593 -9.481481 6.447407 -15.265186c0 -5.973333 -2.37037 -11.662222 -6.731852 -15.739259L376.60444406 116.242963V775.585185c-0.094815 12.041481 9.576296 21.807407 21.428149 21.902222 5.688889 0.094815 11.662222 -2.37037 15.834074 -6.731851l128.284444 -136.05926z";

    private static final String PATH_BODY =
            "M828.01777806 627.01037c22.660741 0.18963 41.14963 -18.10963 41.339259 -40.77037 0.094815 -11.282963 -4.551111 -22.186667 -12.705185 -29.961481L357.16740706 70.257778V775.585185c-0.18963 22.660741 18.10963 41.14963 40.770371 41.339259 11.282963 0.094815 22.186667 -4.551111 29.961481 -12.705185l107.994074 -114.536296 115.010371 270.032593c8.628148 20.954074 32.616296 30.90963 53.57037 22.281481 0.094815 -0.094815 0.284444 -0.094815 0.379259 -0.18963l69.12 -28.065185c20.954074 -8.628148 30.90963 -32.616296 22.281482 -53.57037 -0.094815 -0.094815 -0.094815 -0.284444 -0.18963 -0.379259l-115.01037 -272.687408h146.962963z";

    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path outlinePath;
    private Path bodyPath;
    private int lastBg = Integer.MIN_VALUE;

    public GamepadCursorView(Context context) {
        super(context);
        applyAdaptive(0xFF171717);
    }

    /** v0.4.16：按背景色动态取色——主体=互补色相+亮度反转（高饱和），
     *  描边=按背景亮度取白/黑。背景红→青箭头、背景蓝→橙箭头、
     *  背景白→黑箭头、背景黑→白箭头，连续动态调整。 */
    public void setAdaptive(int bgColor) {
        if (lastBg == bgColor) {
            return;
        }
        lastBg = bgColor;
        applyAdaptive(bgColor);
        invalidate();
    }

    private void applyAdaptive(int bgColor) {
        float d = getResources().getDisplayMetrics().density;
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(bgColor, hsv);
        // 主体：互补色相 + 高饱和 + 亮度反转（亮背景暗箭头/暗背景亮箭头）
        float bodyHue = (hsv[0] + 180f) % 360f;
        float bodySat = Math.max(hsv[1], 0.85f);
        float bodyVal = hsv[2] > 0.5f ? 0.12f : 0.95f;
        int body = android.graphics.Color.HSVToColor(new float[]{bodyHue, bodySat, bodyVal});
        // 描边：按背景亮度取白/黑，与主体形成双对比
        int outline = hsv[2] > 0.5f ? 0xFFFFFFFF : 0xFF141414;
        int glow = outline;
        outlinePaint.setColor(outline);
        outlinePaint.setStyle(Paint.Style.FILL);
        outlinePaint.setShadowLayer(8 * d, 0, 0, glow & 0x00FFFFFF | 0x99000000);
        bodyPaint.setColor(body);
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setShadowLayer(6 * d, 0, 0, glow & 0x00FFFFFF | 0x66000000);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float d = getResources().getDisplayMetrics().density;
        if (outlinePath == null) {
            try {
                outlinePath = PathParser.createPathFromPathData(PATH_OUTLINE);
                outlinePath.setFillType(Path.FillType.EVEN_ODD);
                bodyPath = PathParser.createPathFromPathData(PATH_BODY);
            } catch (Throwable t) {
                outlinePath = null;
                return;
            }
        }
        // v0.4.15：箭头 30×16.9dp 内嵌在 (15,15)dp；viewport 1820×1024
        float sx = 30 * d / 1820f;
        float sy = 16.9f * d / 1024f;
        canvas.save();
        canvas.translate(15 * d, 15 * d);
        canvas.scale(sx, sy);
        canvas.drawPath(outlinePath, outlinePaint);
        canvas.drawPath(bodyPath, bodyPaint);
        canvas.restore();
    }
}
