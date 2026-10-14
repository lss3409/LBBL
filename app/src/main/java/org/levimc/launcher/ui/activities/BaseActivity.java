package org.levimc.launcher.ui.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.DisplayCutout;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;

import org.levimc.launcher.R;
import org.levimc.launcher.core.news.NewsFeed;
import org.levimc.launcher.core.news.NewsRepository;
import org.levimc.launcher.core.news.NewsState;
import org.levimc.launcher.core.auth.MsftAccountStore;
import org.levimc.launcher.settings.FeatureSettings;
import org.levimc.launcher.ui.animation.DynamicAnim;
import org.levimc.launcher.util.AccountTextUtils;
import org.levimc.launcher.util.PersonalizationManager;
import org.levimc.launcher.util.ThemeManager;
import org.levimc.launcher.util.UiScaleManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BaseActivity extends AppCompatActivity {
    private int appliedThemeGeneration = -1;
    private int appliedPersonalizationGeneration = -1;
    private boolean navBarInjected = false;
    /** v0.2.0：手柄摇杆方向去抖时间戳。 */
    private long lastGamepadNav;
    private final OkHttpClient navAvatarClient = new OkHttpClient();
    private final ExecutorService navAccountExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<Intent> navAccountLoginLauncher;
    private boolean newsReceiverRegistered;
    private final BroadcastReceiver newsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshNewsBadge();
            onNewsChanged();
        }
    };

    // ---------- v0.2.0/v0.3.0：手柄支持 ----------
    // D-pad/左摇杆=焦点导航；右摇杆=虚拟光标（类似鼠标，A 键点击光标处）；
    // A=确认；B=返回；LB/RB=切换底部导航栏位（子类覆盖 onGamepadTabSwitch）。

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            // 手柄任何按键：退出触屏模式，恢复焦点描边（触屏时被隐藏）
            onGamepadInputResume();
            // v0.4.10：非 A 键 = 显式切回焦点模式（A 键在光标可见时
            // 是光标点击语义，不切模式）
            if (isGamepadCursorVisible()
                    && event.getKeyCode() != android.view.KeyEvent.KEYCODE_BUTTON_A) {
                hideGamepadCursor();
                restoreOutlineAfterCursor();
            }
            switch (event.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    // v0.4.10：D-pad = 显式切回焦点模式（moveGamepadFocus
                    // 内部先隐藏光标恢复描边再导航）
                    return moveGamepadFocus(event.getKeyCode()
                            == android.view.KeyEvent.KEYCODE_DPAD_UP ? View.FOCUS_UP
                            : event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_DOWN
                            ? View.FOCUS_DOWN
                            : event.getKeyCode() == android.view.KeyEvent.KEYCODE_DPAD_LEFT
                            ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
                case android.view.KeyEvent.KEYCODE_BUTTON_A:
                    // 光标可见 → 点击光标位置；否则确认焦点视图
                    if (isGamepadCursorVisible()) {
                        clickAtGamepadCursor();
                    } else {
                        clickGamepadFocused();
                    }
                    return true;
                case android.view.KeyEvent.KEYCODE_BUTTON_B:
                    getOnBackPressedDispatcher().onBackPressed();
                    return true;
                case android.view.KeyEvent.KEYCODE_BUTTON_L1:
                    return onGamepadTabSwitch(-1);
                case android.view.KeyEvent.KEYCODE_BUTTON_R1:
                    return onGamepadTabSwitch(1);
                // v0.4.7：R3 按住 = 拖拽模式（配合右摇杆拖动地图/视图）
                case android.view.KeyEvent.KEYCODE_BUTTON_THUMBR:
                    r3Held = true;
                    return true;
                // v0.4.7：X=缩小 Y=放大（地图页缩放卫星图；滚动改 LT/RT）
                case android.view.KeyEvent.KEYCODE_BUTTON_X:
                    return onGamepadZoom(1 / 1.25f);
                case android.view.KeyEvent.KEYCODE_BUTTON_Y:
                    return onGamepadZoom(1.25f);
                default:
                    break;
            }
        } else if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
            if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BUTTON_THUMBR) {
                r3Held = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
            // v0.4.7：LT/RT 触发 = 上下滑动页面（替代 v0.4.4 的 X/Y 滚动键）
            handleGamepadTriggers(event);
            // v0.3.0：右摇杆（AXIS_Z/AXIS_RZ）移动虚拟光标
            float rx = event.getAxisValue(android.view.MotionEvent.AXIS_Z);
            float ry = event.getAxisValue(android.view.MotionEvent.AXIS_RZ);
            // v0.4.3：无条件记录方向——归中事件必须清零，否则帧回调
            // 读到陈旧值光标持续漂移
            cursorRx = rx;
            cursorRy = ry;
            if (Math.abs(rx) > 0.15f || Math.abs(ry) > 0.15f) {
                moveGamepadCursor();
                return true;
            }
            // v0.4.10：左摇杆 = 显式切回焦点模式（moveGamepadFocus
            // 内部先隐藏光标恢复描边再导航），不再"光标让位吞掉输入"
            onGamepadInputResume();
            float hatX = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(android.view.MotionEvent.AXIS_HAT_Y);
            float stickX = event.getAxisValue(android.view.MotionEvent.AXIS_X);
            float stickY = event.getAxisValue(android.view.MotionEvent.AXIS_Y);
            float dx = Math.abs(hatX) >= Math.abs(stickX) ? hatX : stickX;
            float dy = Math.abs(hatY) >= Math.abs(stickY) ? hatY : stickY;
            int dir = -1;
            if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                dir = Math.abs(dx) > Math.abs(dy)
                        ? (dx > 0 ? View.FOCUS_RIGHT : View.FOCUS_LEFT)
                        : (dy > 0 ? View.FOCUS_DOWN : View.FOCUS_UP);
            }
            if (dir >= 0) {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - lastGamepadNav >= 220) { // 摇杆去抖：220ms 一步
                    lastGamepadNav = now;
                    moveGamepadFocus(dir);
                }
                return true; // 吞掉摇杆事件，避免系统重复处理
            }
        }
        return super.dispatchGenericMotionEvent(event);
    }

    private boolean moveGamepadFocus(int direction) {
        // v0.4.10：焦点导航 = 显式切回焦点模式（光标可见时先隐藏并
        // 恢复描边，不再"光标让位吞掉输入"）
        if (isGamepadCursorVisible()) {
            hideGamepadCursor();
            restoreOutlineAfterCursor();
        }
        View current = getCurrentFocus();
        if (current == null) {
            // v0.4.4：无焦点（刚切 tab/触屏清焦后）→ 落到子类指定首页
            // 视图（底部 tab 按钮）或第一个可聚焦项，恢复可导航状态
            // （此前 focusSearch 从 null 出发找不到方向项=切 tab 后
            // 摇杆失灵根因）
            View first = gamepadFirstFocusView();
            if (first == null) {
                first = findFirstFocusable();
            }
            if (first != null && first.isShown()) {
                first.requestFocus();
                return true;
            }
            return false;
        }
        View next = current.focusSearch(direction);
        if (next != null && next != current) {
            next.requestFocus();
            return true;
        }
        // v0.4.4：方向到头（列表底部/顶部）→ 滚动内容翻页，
        // 「底部还有信息往下翻」由这里与 X/Y 键共同保证
        return scrollGamepadContent(direction == View.FOCUS_DOWN || direction == View.FOCUS_RIGHT
                ? 1 : -1);
    }

    /** 手柄导航的首页焦点视图（当前底部 tab 按钮）；子类可覆盖。
     *  v0.4.4：切 tab 后焦点落这里+描边，恢复摇杆可导航状态。 */
    protected View gamepadFirstFocusView() {
        int id = -1;
        if (this instanceof MainActivity) {
            id = R.id.nav_tab_launch;
        } else if (this instanceof InstancesActivity) {
            id = R.id.nav_tab_instances;
        } else if (this instanceof OnlineActivity) {
            id = R.id.nav_tab_online;
        } else if (this instanceof SettingsActivity) {
            id = R.id.nav_tab_settings;
        }
        return id > 0 ? findViewById(id) : null;
    }

    /** 深度优先找第一个可聚焦且可见的 View（兜底初始焦点）。 */
    private View findFirstFocusable() {
        View content = getWindow().getDecorView().findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return null;
        }
        return findFirstFocusableIn((ViewGroup) content);
    }

    private View findFirstFocusableIn(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!child.isShown()) {
                continue;
            }
            if (child.isFocusable()) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View found = findFirstFocusableIn((ViewGroup) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** v0.4.4：滚动当前焦点所在的可滚动容器（LT/RT 与焦点导航到
     *  边界时共用）。向上/下滚动约 1/3 屏。
     *  v0.4.11：Activity 树找不到时查活动弹窗（导入弹窗等 Dialog
     *  是独立窗口，不在 Activity decor 树里——"向下滑动按键在导入
     *  菜单没生效"根因）。 */
    private boolean scrollGamepadContent(int direction) {
        View anchor = getCurrentFocus();
        if (anchor == null) {
            anchor = getWindow().getDecorView().findViewById(android.R.id.content);
        }
        android.graphics.Point size = new android.graphics.Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        int step = Math.max(160, size.y / 3);
        if (anchor != null) {
            for (android.view.ViewParent p = anchor.getParent();
                    p instanceof View; p = p.getParent()) {
                View pv = (View) p;
                if (pv.canScrollVertically(direction)) {
                    pv.scrollBy(0, direction * step);
                    return true;
                }
            }
        }
        // 活动弹窗内找（Dialog 独立窗口）
        android.app.Dialog dlg = sActiveGamepadDialog;
        if (dlg != null && dlg.isShowing() && dlg.getWindow() != null) {
            View dec = dlg.getWindow().getDecorView();
            if (dec instanceof ViewGroup) {
                View sc = findScrollableIn((ViewGroup) dec, direction);
                if (sc != null) {
                    sc.scrollBy(0, direction * step);
                    return true;
                }
            }
        }
        return false;
    }

    private View findScrollableIn(ViewGroup group, int direction) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.canScrollVertically(direction)) {
                return child;
            }
            if (child instanceof ViewGroup) {
                View found = findScrollableIn((ViewGroup) child, direction);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean clickGamepadFocused() {
        View current = getCurrentFocus();
        if (current != null) {
            current.performClick();
            return true;
        }
        return false;
    }

    // ---------- v0.3.0：虚拟光标（右摇杆，类鼠标引导） ----------
    private android.view.View gamepadCursor;
    /** v0.4.4：光标位置进程级静态——切 tab=切 Activity，实例字段会归
     *  初始值导致光标回屏幕中心；static 跨 Activity 保留。 */
    private static float cursorX = -1, cursorY = -1;
    /** 摇杆最新方向（轴事件只更新这两个值，位置由 vsync 帧回调推进）。 */
    private float cursorRx, cursorRy;
    private boolean cursorFramePosted;
    private long cursorLastFrameNanos;
    /** 光标可见期间焦点描边被压制（避免「光标在动、描边还在跳」的
     *  双指示器冲突），隐藏后恢复。 */
    private boolean outlineSuppressedByCursor;
    /** v0.4.10：摇杆归中 5s 后自动隐藏光标。模式不自动切换——
     *  只有主动动左摇杆/D-pad/其它按键才切回焦点模式（描边恢复）。 */
    private final Runnable cursorHideTask = new Runnable() {
        @Override
        public void run() {
            hideGamepadCursor();
        }
    };
    /** v0.4.3：帧驱动光标——事件驱动受轴事件到达间隔抖动影响（蓝牙
     *  8~32ms 随机），16ms 节流后更新间隔 16~32ms 跳格。改为
     *  Choreographer 每 vsync 推进一次，与屏幕刷新严格同步；位移按
     *  帧间隔换算（约 1375px/s，帧率波动时速度恒定）。 */
    private final android.view.Choreographer.FrameCallback cursorFrame =
            new android.view.Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (gamepadCursor == null) {
                        cursorFramePosted = false;
                        return;
                    }
                    boolean active = Math.abs(cursorRx) > 0.15f
                            || Math.abs(cursorRy) > 0.15f;
                    if (!active) {
                        // 摇杆回中：停帧，5s 后自动隐藏（v0.4.10 改，
                        // 此前 600ms 太短"光标立刻消失"）；期间再动
                        // 摇杆则取消隐藏。隐藏不恢复描边——模式切换
                        // 只由左摇杆/D-pad/其它按键显式触发
                        cursorFramePosted = false;
                        cursorLastFrameNanos = 0;
                        gamepadCursor.removeCallbacks(cursorHideTask);
                        gamepadCursor.postDelayed(cursorHideTask, 5000);
                        return;
                    }
                    gamepadCursor.removeCallbacks(cursorHideTask);
                    if (cursorX < 0) {
                        cursorX = getResources().getDisplayMetrics().widthPixels / 2f;
                        cursorY = getResources().getDisplayMetrics().heightPixels / 2f;
                    }
                    long dt = cursorLastFrameNanos == 0
                            ? 16_666_666L : frameTimeNanos - cursorLastFrameNanos;
                    cursorLastFrameNanos = frameTimeNanos;
                    // v0.4.11：灵敏度可调（设置页 0.5x-3.0x）
                    float speedFactor = 1f;
                    try {
                        speedFactor = getSharedPreferences("settings", MODE_PRIVATE)
                                .getFloat("gamepad_cursor_speed", 1f);
                    } catch (Throwable ignored) {
                    }
                    float step = 1375f * speedFactor * (dt / 1e9f);
                    if (r3Held) {
                        // v0.4.7：R3+右摇杆=拖拽视图（类似鼠标按住右键
                        // 拖动），光标隐藏、位置不动
                        if (gamepadCursor.getVisibility() == android.view.View.VISIBLE) {
                            gamepadCursor.setVisibility(android.view.View.GONE);
                        }
                        onGamepadMapPan(cursorRx * step, cursorRy * step);
                        android.view.Choreographer.getInstance()
                                .postFrameCallback(this);
                        return;
                    }
                    float d = getResources().getDisplayMetrics().density;
                    cursorX = Math.max(0, Math.min(
                            getResources().getDisplayMetrics().widthPixels,
                            cursorX + cursorRx * step));
                    cursorY = Math.max(0, Math.min(
                            getResources().getDisplayMetrics().heightPixels,
                            cursorY + cursorRy * step));
                    // 尖端锚定：箭头尖端在 viewport (357,70)/1820×1024，
                    // 内嵌偏移 (12,12)dp（v0.4.10 光晕版）
                    gamepadCursor.setTranslationX(cursorX - 16.7f * d);
                    gamepadCursor.setTranslationY(cursorY - 12.9f * d);
                    if (gamepadCursor.getVisibility() != android.view.View.VISIBLE) {
                        gamepadCursor.setVisibility(android.view.View.VISIBLE);
                        // 光标出现：压制焦点描边，输入只信任光标
                        View f = getCurrentFocus();
                        if (f != null) {
                            clearFocusOutline(f);
                            outlineSuppressedByCursor = true;
                        }
                    }
                    // v0.4.7：悬停回调（地图页显示群系名）
                    onGamepadCursorHover(cursorX, cursorY);
                    android.view.Choreographer.getInstance().postFrameCallback(this);
                }
            };

    private void ensureGamepadCursor() {
        if (gamepadCursor != null) {
            return;
        }
        // v0.4.3：用户提供 SVG 贴图（白箭头 + 黑描边）
        // v0.4.11：改 GamepadCursorView——shadowLayer 箭头自身发光
        // （v0.4.10 独立光晕背景层被用户否决）；View 48×48，箭头
        // 24×13.5 内嵌 (12,12)，尖端锚点 (12+4.7, 12+0.9)dp
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (48 * d);
        org.levimc.launcher.ui.views.GamepadCursorView iv =
                new org.levimc.launcher.ui.views.GamepadCursorView(this);
        iv.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        // 高 elevation 保证图层排序盖过按钮/FAB；outline 置空不画系统
        // 阴影（贴图自带黑描边）。translation 每帧变化时系统阴影区域
        // 整块重绘是卡顿来源之一
        iv.setElevation(80 * d);
        iv.setOutlineProvider(null);
        iv.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        iv.setVisibility(View.GONE);
        ViewGroup decor = (ViewGroup) getWindow().getDecorView();
        decor.addView(iv);
        gamepadCursor = iv;
    }

    /** 右摇杆轴事件：启动帧回调（方向值已在 dispatch 处更新）。 */
    private void moveGamepadCursor() {
        ensureGamepadCursor();
        if (!cursorFramePosted) {
            cursorFramePosted = true;
            android.view.Choreographer.getInstance().postFrameCallback(cursorFrame);
        }
    }

    // ---------- v0.4.7：地图操作组合键 ----------
    /** R3 按住状态：按住 R3 时右摇杆从移动光标变为拖动视图
     *  （类似鼠标按住右键拖动）。 */
    private boolean r3Held;
    private long lastGamepadScroll;
    /** LT/RT 触发：滚动当前可滚动容器（上下滑页）。 */
    private void handleGamepadTriggers(android.view.MotionEvent event) {
        float lt = event.getAxisValue(android.view.MotionEvent.AXIS_BRAKE);
        float rt = event.getAxisValue(android.view.MotionEvent.AXIS_GAS);
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastGamepadScroll < 220) {
            return;
        }
        int dir = 0;
        if (lt > 0.5f) {
            dir = -1; // LT = 上滑
        } else if (rt > 0.5f) {
            dir = 1;  // RT = 下滑
        }
        if (dir != 0) {
            lastGamepadScroll = now;
            onGamepadScroll(dir);
        }
    }

    /** 地图/视图缩放（X=缩小 Y=放大）；默认滚页面内容兜底。 */
    protected boolean onGamepadZoom(float factor) {
        return false;
    }

    /** LT/RT 滚动页面；默认 scrollGamepadContent。 */
    protected boolean onGamepadScroll(int dir) {
        return scrollGamepadContent(dir);
    }

    /** R3+右摇杆拖动视图（每帧 dx/dy 像素）；默认不处理。 */
    protected void onGamepadMapPan(float dxPx, float dyPx) {
    }

    /** 光标位置悬停回调（显示群系名等）；默认不处理。 */
    protected void onGamepadCursorHover(float x, float y) {
    }

    private boolean isGamepadCursorVisible() {
        return gamepadCursor != null && gamepadCursor.getVisibility() == View.VISIBLE;
    }

    /** A 键：在光标位置模拟一次触摸点击（带按下动画反馈）。 */
    private void clickAtGamepadCursor() {
        ViewGroup root = getWindow().getDecorView().findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(
                now, now, android.view.MotionEvent.ACTION_DOWN, cursorX, cursorY, 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(
                now, now + 60, android.view.MotionEvent.ACTION_UP, cursorX, cursorY, 0);
        // v0.4.0：按下反馈——箭头缩小再回弹，模拟鼠标点击手感
        gamepadCursor.animate().scaleX(0.7f).scaleY(0.7f).setDuration(60)
                .withEndAction(() -> gamepadCursor.animate()
                        .scaleX(1f).scaleY(1f).setDuration(90).start())
                .start();
        try {
            root.dispatchTouchEvent(down);
            root.dispatchTouchEvent(up);
        } finally {
            down.recycle();
            up.recycle();
        }
        gamepadCursor.postDelayed(this::hideGamepadCursor, 160);
    }

    private void hideGamepadCursor() {
        if (gamepadCursor != null) {
            gamepadCursor.removeCallbacks(cursorHideTask);
            gamepadCursor.setVisibility(View.GONE);
        }
        // v0.4.10：隐藏不再恢复描边——模式切换只由左摇杆/D-pad/
        // 其它按键显式触发（restoreOutlineAfterCursor 改由
        // moveGamepadFocus 与手柄按键调用）
        onGamepadCursorHidden();
    }

    /** v0.4.7：光标隐藏回调（地图页恢复 HUD 坐标显示等）。 */
    protected void onGamepadCursorHidden() {
    }

    /** 光标隐藏后恢复焦点描边（若期间被压制）。由焦点输入显式触发。 */
    private void restoreOutlineAfterCursor() {
        if (!outlineSuppressedByCursor) {
            return;
        }
        outlineSuppressedByCursor = false;
        View f = getCurrentFocus();
        if (f != null) {
            applyFocusOutline(f);
        }
    }

    /** v0.4.4：手柄任意输入（按键/摇杆）时退出触屏模式——触屏期间
     *  被隐藏的焦点描边恢复显示。 */
    private void onGamepadInputResume() {
        if (!outlineSuppressedByTouch) {
            return;
        }
        outlineSuppressedByTouch = false;
        if (isGamepadCursorVisible()) {
            return; // 光标还活跃：继续压制
        }
        View f = getCurrentFocus();
        if (f != null) {
            applyFocusOutline(f);
        }
    }

    /** 触屏期间描边被隐藏（用户转用手指）。 */
    private boolean outlineSuppressedByTouch;

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        // v0.4.4：手指触摸屏幕 → 光标与焦点描边立刻消失（切回触屏模式）。
        // clickAtGamepadCursor 的模拟事件走 root.dispatchTouchEvent，
        // 不经过 Activity 层，不会误触发
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
            if (isGamepadCursorVisible()) {
                hideGamepadCursor();
            }
            View f = getCurrentFocus();
            if (f != null) {
                clearFocusOutline(f);
                outlineSuppressedByTouch = true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // ---------- v0.4.0：手柄焦点描边 ----------
    /** 焦点变化时给当前焦点视图加强调色描边（手柄导航视觉引导）。 */
    private void setupGamepadFocusOutline() {
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener(
                (oldFocus, newFocus) -> {
                    clearFocusOutline(oldFocus);
                    // v0.4.4：触屏/光标压制期间不描边（恢复时主动补描）
                    if (!outlineSuppressedByTouch && !outlineSuppressedByCursor) {
                        applyFocusOutline(newFocus);
                    }
                });
    }

    private void applyFocusOutline(View v) {
        applyFocusOutlineStatic(this, v);
    }

    /** v0.4.11：静态版描边（CustomAlertDialog 的 Dialog 窗口复用）。 */
    public static void applyFocusOutlineStatic(android.content.Context ctx, View v) {
        if (v == null) {
            return;
        }
        try {
            int accent = 0xFF5B9CF6;
            try {
                int a = new PersonalizationManager(ctx).getAccentColor();
                if (a != 0) {
                    accent = a;
                }
            } catch (Throwable ignored) {
            }
            float d = ctx.getResources().getDisplayMetrics().density;
            android.graphics.drawable.GradientDrawable outline =
                    new android.graphics.drawable.GradientDrawable();
            outline.setStroke((int) (2.5f * d), accent);
            outline.setCornerRadius(10 * d);
            v.setForeground(outline);
        } catch (Throwable ignored) {
        }
    }

    private void clearFocusOutline(View v) {
        clearFocusOutlineStatic(v);
    }

    /** v0.4.11：静态版清描边。 */
    public static void clearFocusOutlineStatic(View v) {
        if (v != null) {
            try {
                v.setForeground(null);
            } catch (Throwable ignored) {
            }
        }
    }

    /** v0.4.11：当前活动的手柄感知弹窗（LT/RT 滚动键在弹窗内
     *  找滚动容器用；弹窗内焦点描边由 CustomAlertDialog 自挂监听）。 */
    private static android.app.Dialog sActiveGamepadDialog;

    public static void registerActiveGamepadDialog(android.app.Dialog d) {
        sActiveGamepadDialog = d;
    }

    public static void unregisterActiveGamepadDialog(android.app.Dialog d) {
        if (sActiveGamepadDialog == d) {
            sActiveGamepadDialog = null;
        }
    }

    /** v0.3.0：LB(-1)/RB(+1) 切换底部导航栏位（4 tab 循环）。 */
    protected boolean onGamepadTabSwitch(int delta) {
        if (!navBarInjected) {
            return false;
        }
        int cur = -1;
        if (this instanceof MainActivity) {
            cur = 0;
        } else if (this instanceof InstancesActivity) {
            cur = 1;
        } else if (this instanceof OnlineActivity) {
            cur = 2;
        } else if (this instanceof SettingsActivity) {
            cur = 3;
        }
        if (cur < 0) {
            return false;
        }
        int next = (cur + delta + 4) % 4;
        // v0.4.4：标记手柄切 tab 来源——新 Activity 自动把焦点落到
        // 当前 tab 按钮并描边（此前切 tab 后无焦点无描边，
        // 摇杆导航失灵）
        Intent target = null;
        switch (next) {
            case 0:
                target = new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                break;
            case 1:
                target = new Intent(this, InstancesActivity.class);
                break;
            case 2:
                target = new Intent(this, OnlineActivity.class);
                break;
            default:
                target = new Intent(this, SettingsActivity.class);
                break;
        }
        target.putExtra("gamepad_tab_nav", true);
        startActivity(target);
        return true;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String languageCode = prefs.getString("language", Locale.getDefault().toLanguageTag());
        Locale locale = Locale.forLanguageTag(languageCode);
        Locale.setDefault(locale);
        Resources res = newBase.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);
        Context localizedContext = newBase.createConfigurationContext(config);
        Context scaledContext = UiScaleManager.applyScale(localizedContext);
        super.attachBaseContext(scaledContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager themeManager = new ThemeManager(this);
        themeManager.applyTheme();
        appliedThemeGeneration = ThemeManager.getThemeChangeGeneration();
        appliedPersonalizationGeneration = PersonalizationManager.getChangeGeneration();
        super.onCreate(savedInstanceState);
        navAccountLoginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleNavAccountLoginResult(result.getResultCode(), result.getData()));
        applyCutoutMode();
        hideSystemUI();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                visibility -> getWindow().getDecorView().post(this::hideSystemUI));
        setupGamepadFocusOutline();
    }

    /** 异形屏适配：内容允许延伸进 cutout 区域，避免系统强制留黑边/白边。 */
    private void applyCutoutMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    /** 给内容根视图加 cutout/圆角安全边距（挖孔与圆角屏不被内容遮挡）。 */
    private void applySafeInsets(View root) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return;
        }
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left = 0, top = 0, right = 0, bottom = 0;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets cutout =
                            insets.getInsets(WindowInsets.Type.displayCutout());
                    left = Math.max(left, cutout.left);
                    top = Math.max(top, cutout.top);
                    right = Math.max(right, cutout.right);
                    bottom = Math.max(bottom, cutout.bottom);
                    // 圆角屏安全区（四个角）
                    try {
                        for (int pos : new int[]{
                                android.view.RoundedCorner.POSITION_TOP_LEFT,
                                android.view.RoundedCorner.POSITION_TOP_RIGHT,
                                android.view.RoundedCorner.POSITION_BOTTOM_LEFT,
                                android.view.RoundedCorner.POSITION_BOTTOM_RIGHT}) {
                            android.view.RoundedCorner corner = insets.getRoundedCorner(pos);
                            if (corner != null && corner.getRadius() > 0) {
                                int r = corner.getRadius();
                                if (pos == android.view.RoundedCorner.POSITION_TOP_LEFT
                                        || pos == android.view.RoundedCorner.POSITION_BOTTOM_LEFT) {
                                    left = Math.max(left, r / 2);
                                } else {
                                    right = Math.max(right, r / 2);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                } else {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            } catch (Throwable ignored) {
            }
            v.setPadding(left, top, right, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    @Override
    public void setContentView(int layoutResID) {
        View contentView = LayoutInflater.from(this).inflate(layoutResID, null);
        wrapWithNavBar(contentView);
    }

    @Override
    public void setContentView(View view) {
        wrapWithNavBar(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        wrapWithNavBar(view);
    }

    private void wrapWithNavBar(View contentView) {
        if (shouldSkipNavBar()) {
            applySafeInsets(contentView);
            super.setContentView(contentView);
            applyPersonalization();
            return;
        }

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View navBar = LayoutInflater.from(this).inflate(R.layout.nav_bar, wrapper, false);
        wrapper.addView(navBar);

        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        contentView.setLayoutParams(contentParams);
        wrapper.addView(contentView);
        applySafeInsets(wrapper);

        contentView.setAlpha(0f);
        contentView.setTranslationY(8f * getResources().getDisplayMetrics().density);

        super.setContentView(wrapper);
        navBarInjected = true;
        setupBaseNavBar();

        applyPersonalization();

        contentView.post(() -> {
            DynamicAnim.springAlphaTo(contentView, 1f).start();
            DynamicAnim.springTranslationYTo(contentView, 0f).start();
        });
    }

    private void applyPersonalization() {
        PersonalizationManager pm = new PersonalizationManager(this);
        pm.applyToActivity(this);
    }

    protected boolean shouldSkipNavBar() {
        return false;
    }

    private void setupBaseNavBar() {
        int[] tabIds = {
            R.id.nav_tab_launch, R.id.nav_tab_instances,
            R.id.nav_tab_online, R.id.nav_tab_settings
        };

        PersonalizationManager pm = new PersonalizationManager(this);
        int accent = pm.getAccentColor();

        for (int id : tabIds) {
            TextView tab = findViewById(id);
            if (tab == null) continue;
            int color = getResources().getColor(R.color.text_secondary, getTheme());
            tab.setTextColor(color);
            tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.NORMAL);
            TextViewCompat.setCompoundDrawableTintList(tab, ColorStateList.valueOf(color));
            // v0.4.4：tab 可聚焦——切 tab 后焦点落到当前 tab 并描边。
            // v0.4.9：去掉 setFocusableInTouchMode(true)——touch mode 下
            // 点击抢焦点不触发 click，触摸切栏位要双击（用户实测回归），
            // 且抢焦点触发描边"不识别触摸"。手柄按键输入时系统自动
            // 退出 touch mode，焦点导航不受影响
            tab.setFocusable(true);
        }

        if (pm.hasBackgroundImage()) {
            View navRoot = findViewById(R.id.nav_bar_root);
            if (navRoot != null) {
                boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                navRoot.setBackgroundColor(isDark
                        ? android.graphics.Color.argb(90, 25, 25, 25)
                        : android.graphics.Color.argb(110, 255, 255, 255));
            }
            View navDivider = findViewById(R.id.nav_divider);
            if (navDivider != null) {
                boolean isDark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
                navDivider.setBackgroundColor(isDark
                        ? android.graphics.Color.argb(40, 255, 255, 255)
                        : android.graphics.Color.argb(40, 0, 0, 0));
            }
        }

        View backButton = findViewById(R.id.nav_back_button);
        if (backButton != null) {
            backButton.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
            DynamicAnim.applyPressScale(backButton);
            // v0.4.10：返回按钮不可聚焦——B 键已处理返回，若可聚焦则
            // 焦点导航落到它时 A 键会触发退出（"A 键一会点击一会退出"根因）
            backButton.setFocusable(false);
        }

        View signIn = findViewById(R.id.nav_sign_in_button);
        if (signIn != null) {
            signIn.setOnClickListener(v -> navAccountLoginLauncher.launch(new Intent(this, MsftLoginActivity.class)));
            DynamicAnim.applyPressScale(signIn);
        }

        View avatarContainer = findViewById(R.id.nav_account_avatar_container);
        if (avatarContainer != null) {
            avatarContainer.setOnClickListener(v -> startActivity(new Intent(this, AccountsActivity.class)));
            DynamicAnim.applyPressScale(avatarContainer);
        }

        View news = findViewById(R.id.nav_news_container);
        if (news != null) {
            news.setOnClickListener(v -> {
                if (!(this instanceof NewsActivity)) {
                    startActivity(new Intent(this, NewsActivity.class));
                }
            });
            DynamicAnim.applyPressScale(news);
        }

        findViewById(R.id.nav_tab_launch).setOnClickListener(v -> {
            if (!(this instanceof MainActivity)) {
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
            }
        });
        findViewById(R.id.nav_tab_instances).setOnClickListener(v -> {
            if (!(this instanceof InstancesActivity)) {
                startActivity(new Intent(this, InstancesActivity.class));
            }
        });
        findViewById(R.id.nav_tab_online).setOnClickListener(v -> {
            if (!(this instanceof OnlineActivity)) {
                startActivity(new Intent(this, OnlineActivity.class));
            }
        });
        findViewById(R.id.nav_tab_settings).setOnClickListener(v -> {
            if (!(this instanceof SettingsActivity)) {
                startActivity(new Intent(this, SettingsActivity.class));
            }
        });

        refreshNavAccountUI();
        refreshNewsBadge();
    }

    private void refreshNewsBadge() {
        if (!navBarInjected) return;
        NewsRepository.loadCached(this, (feed, error) -> applyNewsBadge(feed));
        NewsRepository.refreshIfStale(this, (feed, error) -> applyNewsBadge(feed));
    }

    private void applyNewsBadge(NewsFeed feed) {
        if (isFinishing() || isDestroyed()) return;
        int unread = NewsState.getUnreadCount(this, feed);
        View badge = findViewById(R.id.nav_news_badge);
        View container = findViewById(R.id.nav_news_container);
        if (badge != null) badge.setVisibility(unread > 0 ? View.VISIBLE : View.GONE);
        if (container != null) {
            container.setContentDescription(unread > 0
                    ? getString(R.string.news_unread_description, unread)
                    : getString(R.string.news_title));
        }
    }

    protected void onNewsChanged() {
    }

    protected void refreshNavAccountUI() {
        if (!navBarInjected) return;
        View signIn = findViewById(R.id.nav_sign_in_button);
        View avatarContainer = findViewById(R.id.nav_account_avatar_container);
        // v0.0.11：登录入口一直显示（设置页开关已随 tab 改版移除，
        // msLoginEnabled 无写入方——此前新装用户非主页永远看不到登录入口）
        java.util.List<MsftAccountStore.MsftAccount> list = MsftAccountStore.list(this);
        MsftAccountStore.MsftAccount active = null;
        for (MsftAccountStore.MsftAccount a : list) if (a.active) { active = a; break; }
        if (active == null) {
            if (signIn != null) signIn.setVisibility(View.VISIBLE);
            if (avatarContainer != null) avatarContainer.setVisibility(View.GONE);
            clearNavAvatar();
        } else {
            if (signIn != null) signIn.setVisibility(View.GONE);
            if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);
            loadNavXboxAvatar(active);
        }
    }

    private void clearNavAvatar() {
        com.microsoft.xbox.idp.toolkit.CircleImageView avatar = findViewById(R.id.nav_account_avatar);
        ProgressBar progress = findViewById(R.id.nav_avatar_progress);
        if (avatar != null) avatar.setImageResource(R.drawable.ic_minecraft_cube);
        if (progress != null) progress.setVisibility(View.GONE);
    }

    private void loadNavXboxAvatar(MsftAccountStore.MsftAccount active) {
        com.microsoft.xbox.idp.toolkit.CircleImageView avatar = findViewById(R.id.nav_account_avatar);
        ProgressBar progress = findViewById(R.id.nav_avatar_progress);
        if (avatar == null) return;

        String url = AccountTextUtils.sanitizeUrl(active != null ? active.xboxAvatarUrl : null);
        if (url == null) {
            avatar.setImageResource(R.drawable.ic_minecraft_cube);
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        Object currentUrl = avatar.getTag(R.id.nav_account_avatar);
        if (url.equals(currentUrl) && avatar.getDrawable() != null) {
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        Bitmap cached = AccountTextUtils.getCachedAvatar(url);
        if (cached != null) {
            avatar.setTag(R.id.nav_account_avatar, url);
            avatar.setImageBitmap(cached);
            if (progress != null) progress.setVisibility(View.GONE);
            return;
        }

        avatar.setTag(R.id.nav_account_avatar, url);
        avatar.setImageResource(R.drawable.ic_minecraft_cube);
        if (progress != null) progress.setVisibility(View.VISIBLE);
        navAccountExecutor.execute(() -> {
            Bitmap bmp = null;
            try (Response imgResp = navAvatarClient.newCall(new Request.Builder().url(url).build()).execute()) {
                if (imgResp.isSuccessful() && imgResp.body() != null) {
                    bmp = android.graphics.BitmapFactory.decodeStream(imgResp.body().byteStream());
                }
            } catch (Exception ignored) {
            }

            final Bitmap loaded = bmp;
            runOnUiThread(() -> {
                if (!url.equals(avatar.getTag(R.id.nav_account_avatar))) return;
                if (loaded != null) {
                    AccountTextUtils.cacheAvatar(url, loaded);
                    avatar.setImageBitmap(loaded);
                }
                if (progress != null) progress.setVisibility(View.GONE);
            });
        });
    }

    private void handleNavAccountLoginResult(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null
                && data.getBooleanExtra(MsftLoginActivity.EXTRA_LOGIN_COMPLETED, false)) {
            String name = data.getStringExtra(MsftLoginActivity.EXTRA_LOGIN_NAME);
            String statusName = name != null ? name : getString(R.string.not_signed_in);
            Toast.makeText(this, getString(R.string.ms_login_success, statusName), Toast.LENGTH_SHORT).show();
            refreshNavAccountUI();
            onNavAccountChanged();
            return;
        }
        refreshNavAccountUI();
    }

    protected void onNavAccountChanged() {
    }

    protected void setActiveNavTab(int activeTabId) {
        if (!navBarInjected) return;
        int[] tabIds = {
            R.id.nav_tab_launch, R.id.nav_tab_instances,
            R.id.nav_tab_online, R.id.nav_tab_settings
        };

        PersonalizationManager pm = new PersonalizationManager(this);
        int accent = pm.getAccentColor();

        for (int id : tabIds) {
            TextView tab = findViewById(id);
            if (tab == null) continue;
            int color;
            if (id == activeTabId) {
                color = accent != 0 ? accent : getResources().getColor(R.color.on_surface, getTheme());
                tab.setTextColor(color);
                tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.BOLD);
            } else {
                color = getResources().getColor(R.color.text_secondary, getTheme());
                tab.setTextColor(color);
                tab.setTypeface(tab.getTypeface(), android.graphics.Typeface.NORMAL);
            }
            TextViewCompat.setCompoundDrawableTintList(tab, ColorStateList.valueOf(color));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!newsReceiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    newsReceiver,
                    new IntentFilter(NewsState.ACTION_NEWS_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED
            );
            newsReceiverRegistered = true;
        }
    }

    @Override
    protected void onStop() {
        if (newsReceiverRegistered) {
            unregisterReceiver(newsReceiver);
            newsReceiverRegistered = false;
        }
        super.onStop();
    }

    protected void onResume() {
        super.onResume();
        int currentGen = ThemeManager.getThemeChangeGeneration();
        int currentPGen = PersonalizationManager.getChangeGeneration();
        if (appliedThemeGeneration != currentGen || appliedPersonalizationGeneration != currentPGen) {
            appliedThemeGeneration = currentGen;
            appliedPersonalizationGeneration = currentPGen;
            recreate();
            return;
        }
        getDelegate().applyDayNight();
        hideSystemUI();
        refreshNavAccountUI();
        refreshNewsBadge();
        // v0.4.4：手柄 LB/RB 切 tab 到达 → 焦点落到当前 tab 按钮并描边。
        // v0.4.9：touch mode 下跳过——用户已在触摸时不抢焦点不描边
        if (getIntent().getBooleanExtra("gamepad_tab_nav", false)) {
            getIntent().removeExtra("gamepad_tab_nav");
            View tab = gamepadFirstFocusView();
            if (tab != null) {
                tab.postDelayed(() -> {
                    if (!isFinishing() && !isDestroyed() && tab.isShown()
                            && !tab.isInTouchMode()) {
                        tab.requestFocus();
                        applyFocusOutline(tab);
                    }
                }, 150);
            }
        }
    }

    @Override
    protected void onPause() {
        hideSystemUI();
        // v0.2.0：离开页面暂停视频背景（省电）
        try {
            new PersonalizationManager(this).pauseVideoBackground(this);
        } catch (Throwable ignored) {
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        navAccountExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().applyDayNight();
        hideSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    protected void hideSystemUI() {
        View decorView = getWindow().getDecorView();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        }

        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private boolean shouldSuppressTransition(Intent intent) {
        return intent != null && (intent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        if (!shouldSuppressTransition(intent)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void startActivity(Intent intent, @Nullable Bundle options) {
        super.startActivity(intent, options);
        if (!shouldSuppressTransition(intent)) {
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void finishAfterTransition() {
        super.finishAfterTransition();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
