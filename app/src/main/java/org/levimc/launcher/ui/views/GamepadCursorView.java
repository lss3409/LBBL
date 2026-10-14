package org.levimc.launcher.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import androidx.core.graphics.PathParser;

/**
 * v0.4.11：手柄虚拟光标——白箭头 + 黑描边 + shadowLayer 自发光
 * （v0.4.10 的独立光晕背景层被用户否决，改为箭头本身发光，
 * 参考主流手柄 UI 的发光光标设计）。pathData 来自用户提供的
 * SVG（gamepad_cursor.xml 同源，viewport 1820×1024）。
 * 尖端锚点：(12+4.7, 12+0.9)dp（View 48×48，箭头 24×13.5 内嵌）。
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

    public GamepadCursorView(Context context) {
        super(context);
        outlinePaint.setColor(0xFF1A1A1A);
        outlinePaint.setStyle(Paint.Style.FILL);
        outlinePaint.setShadowLayer(10 * getResources().getDisplayMetrics().density,
                0, 0, 0xB3FFFFFF); // 白色自发光
        bodyPaint.setColor(0xFFFFFFFF);
        bodyPaint.setStyle(Paint.Style.FILL);
        bodyPaint.setShadowLayer(8 * getResources().getDisplayMetrics().density,
                0, 0, 0x99FFFFFF); // 白色自发光
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
        // 箭头 24×13.5dp 内嵌在 (12,12)dp；viewport 1820×1024
        float sx = 24 * d / 1820f;
        float sy = 13.5f * d / 1024f;
        canvas.save();
        canvas.translate(12 * d, 12 * d);
        canvas.scale(sx, sy);
        canvas.drawPath(outlinePath, outlinePaint);
        canvas.drawPath(bodyPath, bodyPaint);
        canvas.restore();
    }
}
