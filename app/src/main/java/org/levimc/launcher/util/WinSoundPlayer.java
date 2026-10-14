package org.levimc.launcher.util;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import org.levimc.launcher.R;
import org.levimc.launcher.settings.FeatureSettings;

/** v0.4.24：Windows XP 音效播放器（SoundPool）。
 *  音效素材来自 Windows XP（微软版权，仅作愚人节彩蛋用途）。
 *  开关：FeatureSettings.winSoundsEnabled（个性化页）。
 *  注意：SoundPool 加载未完成时 play 静默无声——启动音放 Splash
 *  延迟播（加载时间充足），其余音效到用时早已加载完毕。 */
public final class WinSoundPlayer {

    /** 音效枚举——启动 / 弹窗提示 / 错误崩溃 / 成功 / 删除 / 退出。 */
    public static final int SOUND_STARTUP = 0;
    public static final int SOUND_DING = 1;
    public static final int SOUND_ERROR = 2;
    public static final int SOUND_TADA = 3;
    public static final int SOUND_RECYCLE = 4;
    public static final int SOUND_SHUTDOWN = 5;

    private static WinSoundPlayer INSTANCE;

    private final SoundPool pool;
    private final int[] ids = new int[6];

    private WinSoundPlayer(Context context) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(attrs)
                .build();
        int[] raws = {
                R.raw.xp_startup, R.raw.xp_ding, R.raw.xp_error,
                R.raw.xp_tada, R.raw.xp_recycle, R.raw.xp_shutdown
        };
        for (int i = 0; i < raws.length; i++) {
            ids[i] = pool.load(context, raws[i], 1);
        }
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

    /** 播一个音效；开关关闭或加载失败时静默。 */
    public void play(int sound) {
        try {
            if (pool == null || sound < 0 || sound >= ids.length) {
                return;
            }
            if (!FeatureSettings.getInstance().isWinSoundsEnabled()) {
                return;
            }
            int id = ids[sound];
            if (id == 0) {
                return;
            }
            pool.play(id, 1f, 1f, 1, 0, 1f);
        } catch (Exception ignored) {
        }
    }
}
