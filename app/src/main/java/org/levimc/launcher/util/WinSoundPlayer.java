package org.levimc.launcher.util;

import android.content.Context;
import android.media.MediaPlayer;

import org.levimc.launcher.R;
import org.levimc.launcher.settings.FeatureSettings;

/** v0.4.24：Windows 音效播放器——参考文件管理器音频播放器
 *  （FileManagerActivity.openAudio）用 MediaPlayer 播放。
 *  v0.4.25：素材换成 Windows 11 音效（C:\Windows\Media，
 *  raw 由 PowerShell 加 WAV 头转 48kHz/16bit/mono）。
 *  开关：FeatureSettings.winSoundsEnabled（个性化页）；试听
 *  用 force=true 绕过开关。 */
public final class WinSoundPlayer {

    /** 音效枚举——启动 / 弹窗提示 / 错误 / 成功 / 删除 / 退出。 */
    public static final int SOUND_STARTUP = 0;
    public static final int SOUND_DING = 1;
    public static final int SOUND_ERROR = 2;
    public static final int SOUND_TADA = 3;
    public static final int SOUND_RECYCLE = 4;
    public static final int SOUND_SHUTDOWN = 5;

    private static WinSoundPlayer INSTANCE;

    private final Context appContext;
    private final int[] raws = {
            R.raw.win_startup, R.raw.win_ding, R.raw.win_error,
            R.raw.win_tada, R.raw.win_recycle, R.raw.win_shutdown
    };
    private MediaPlayer player;

    private WinSoundPlayer(Context context) {
        appContext = context.getApplicationContext();
    }

    public static WinSoundPlayer get(Context context) {
        if (INSTANCE == null) {
            synchronized (WinSoundPlayer.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WinSoundPlayer(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /** 播一个音效；开关关闭时静默。 */
    public void play(int sound) {
        play(sound, false);
    }

    /** force=true 绕过开关（试听菜单用）。 */
    public void play(int sound, boolean force) {
        try {
            if (sound < 0 || sound >= raws.length) {
                return;
            }
            if (!force && !FeatureSettings.getInstance().isWinSoundsEnabled()) {
                return;
            }
            // 同一播放器重复用：先释放旧实例（短音效不会叠音）
            release();
            player = MediaPlayer.create(appContext, raws[sound]);
            if (player == null) {
                return;
            }
            final MediaPlayer p = player;
            p.setOnCompletionListener(mp -> {
                mp.release();
                if (player == mp) {
                    player = null;
                }
            });
            p.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                if (player == mp) {
                    player = null;
                }
                return true;
            });
            p.start();
        } catch (Exception ignored) {
        }
    }

    private void release() {
        try {
            if (player != null) {
                player.reset();
                player.release();
                player = null;
            }
        } catch (Exception ignored) {
        }
    }
}
