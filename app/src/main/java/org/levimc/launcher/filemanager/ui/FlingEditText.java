package org.levimc.launcher.filemanager.ui;

import android.content.Context;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.widget.EditText;
import android.widget.OverScroller;

/**
 * 带惯性滑动（fling）的 EditText（v0.4.142）。
 * EditText 默认触摸滚动只跟手、松手即停——大文件浏览体验差。
 * 松手时按滑动速度触发 OverScroller.fling，滚动距离随速度变化。
 * 拖选文本（selectionStart != selectionEnd）时不触发，避免选择被惯性打断。
 */
public class FlingEditText extends EditText {

    private final OverScroller scroller = new OverScroller(getContext());
    private VelocityTracker velocityTracker;

    public FlingEditText(Context context) {
        super(context);
    }

    public FlingEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public FlingEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                break;
            case MotionEvent.ACTION_UP:
                // 拖选文本时不触发 fling（选择范围非空=正在拖选）
                if (getSelectionStart() == getSelectionEnd()) {
                    velocityTracker.computeCurrentVelocity(1000);
                    int velocityY = (int) velocityTracker.getYVelocity();
                    if (Math.abs(velocityY) > 500) {
                        startFling(-velocityY);
                    }
                }
                releaseTracker();
                break;
            case MotionEvent.ACTION_CANCEL:
                scroller.forceFinished(true);
                releaseTracker();
                break;
            default:
                break;
        }
        return super.onTouchEvent(ev);
    }

    private void releaseTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    private void startFling(int velocityY) {
        Layout layout = getLayout();
        if (layout == null) return;
        int maxScrollY = Math.max(0, layout.getHeight()
                + getCompoundPaddingTop() + getCompoundPaddingBottom() - getHeight());
        scroller.fling(0, getScrollY(), 0, velocityY, 0, 0, 0, maxScrollY);
        postInvalidateOnAnimation();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(0, scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }
}
