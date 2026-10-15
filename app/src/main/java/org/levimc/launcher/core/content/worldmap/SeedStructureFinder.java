package org.levimc.launcher.core.content.worldmap;

/**
 * v0.4.57：SeedFinder 结构查找（NDK 内嵌，完全离线）。
 * 按世界种子计算 Bedrock 结构生成位置——村庄/沙漠神殿/海底神殿等
 * 17 种。算法来源：cubiomes（MIT）+ chunkbase Bedrock 结构过滤器
 * 重实现（Bfinders）+ SeedFinder 包装（zlib）。
 */
public final class SeedStructureFinder {

    /** 结构类型 ID（与 SeedFinder API 一致）。 */
    public static final int DESERT_PYRAMID = 1;
    public static final int JUNGLE_TEMPLE = 2;
    public static final int SWAMP_HUT = 3;
    public static final int IGLOO = 4;
    public static final int VILLAGE = 5;
    public static final int OCEAN_RUIN = 6;
    public static final int SHIPWRECK = 7;
    public static final int OCEAN_MONUMENT = 8;
    public static final int WOODLAND_MANSION = 9;
    public static final int PILLAGER_OUTPOST = 10;
    public static final int RUINED_PORTAL = 11;
    public static final int RUINED_PORTAL_NETHER = 12;
    public static final int ANCIENT_CITY = 13;
    public static final int BURIED_TREASURE = 14;
    public static final int MINESHAFT = 15;
    public static final int TRAIL_RUINS = 23;
    public static final int TRIAL_CHAMBERS = 24;

    /** 主世界全部结构（除下界废弃传送门 12）。 */
    public static final int[] ALL_OVERWORLD = {
            DESERT_PYRAMID, JUNGLE_TEMPLE, SWAMP_HUT, IGLOO, VILLAGE,
            OCEAN_RUIN, SHIPWRECK, OCEAN_MONUMENT, WOODLAND_MANSION,
            PILLAGER_OUTPOST, RUINED_PORTAL, ANCIENT_CITY, BURIED_TREASURE,
            MINESHAFT, TRAIL_RUINS, TRIAL_CHAMBERS
    };

    /** 结构名 → 中文名（地图标记显示；实体汉化同款做法）。 */
    public static String chineseName(String name) {
        if (name == null) {
            return "";
        }
        switch (name) {
            case "village": return "村庄";
            case "desert_pyramid": return "沙漠神殿";
            case "jungle_temple": return "丛林神庙";
            case "swamp_hut": return "沼泽小屋";
            case "igloo": return "雪屋";
            case "ocean_ruin": return "海底遗迹";
            case "shipwreck": return "沉船";
            case "ocean_monument": return "海底神殿";
            case "woodland_mansion": return "林地府邸";
            case "pillager_outpost": return "掠夺者前哨站";
            case "ruined_portal": return "废弃传送门";
            case "ancient_city": return "远古城市";
            case "buried_treasure": return "埋藏宝藏";
            case "mineshaft": return "废弃矿井";
            case "trail_ruins": return "Trail Ruins";
            case "trial_chambers": return "试炼密室";
            default: return name;
        }
    }

    private static volatile boolean loaded;
    private static volatile boolean loadFailed;

    private SeedStructureFinder() {
    }

    private static boolean ensureLoaded() {
        if (loaded) {
            return true;
        }
        if (loadFailed) {
            return false;
        }
        synchronized (SeedStructureFinder.class) {
            if (loaded) {
                return true;
            }
            if (loadFailed) {
                return false;
            }
            try {
                System.loadLibrary("structurefinder");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                loadFailed = true;
            }
        }
        return loaded;
    }

    /**
     * 扫描种子在 (x,z) 周边 radius 个 chunk 内的结构位置。
     * 返回 JSON：{"results":[{"name","x","z","distance"}...]}；
     * 库不可用返回 null。纯计算毫秒级，建议后台线程调用。
     */
    public static String scan(long seed, double x, double z, int radius,
                              int maxResults, int[] types) {
        if (!ensureLoaded()) {
            return null;
        }
        try {
            return nativeScan(seed, x, z, radius, maxResults, types);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native String nativeScan(long seed, double x, double z,
                                            int radius, int maxResults, int[] types);
}
