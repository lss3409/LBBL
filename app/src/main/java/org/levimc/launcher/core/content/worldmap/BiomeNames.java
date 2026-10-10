package org.levimc.launcher.core.content.worldmap;

import java.util.HashMap;
import java.util.Map;

/**
 * v0.4.7：群系英文名 → 中文名映射（手柄光标悬停显示群系名用）。
 * 覆盖基岩版常见群系；未收录的显示英文名原样。
 */
public final class BiomeNames {

    private static final Map<String, String> ZH = new HashMap<>();

    static {
        // 海洋类
        put("ocean", "海洋");
        put("deep_ocean", "深海");
        put("warm_ocean", "暖水海洋");
        put("lukewarm_ocean", "温水海洋");
        put("cold_ocean", "冷水海洋");
        put("deep_warm_ocean", "暖水深海");
        put("deep_lukewarm_ocean", "温水深海");
        put("deep_cold_ocean", "冷水深海");
        put("frozen_ocean", "冻洋");
        put("deep_frozen_ocean", "冰冻深海");
        // 陆地类
        put("plains", "平原");
        put("sunflower_plains", "向日葵平原");
        put("desert", "沙漠");
        put("desert_hills", "沙漠丘陵");
        put("mountains", "山地");
        put("wooded_mountains", "繁茂山地");
        put("gravelly_mountains", "沙砾山地");
        put("forest", "森林");
        put("flower_forest", "繁花森林");
        put("birch_forest", "桦木森林");
        put("birch_forest_hills", "桦木森林丘陵");
        put("dark_forest", "黑森林");
        put("swamp", "沼泽");
        put("jungle", "丛林");
        put("jungle_hills", "丛林丘陵");
        put("modified_jungle", "丛林边缘");
        put("bamboo_jungle", "竹林");
        put("bamboo_jungle_hills", "竹林丘陵");
        put("taiga", "针叶林");
        put("taiga_hills", "针叶林丘陵");
        put("giant_tree_taiga", "巨型针叶林");
        put("snowy_taiga", "积雪针叶林");
        put("snowy_tundra", "雪原");
        put("ice_spikes", "冰刺平原");
        put("mushroom_fields", "蘑菇岛");
        put("savanna", "热带草原");
        put("savanna_plateau", "热带高原");
        put("badlands", "恶地");
        put("eroded_badlands", "风蚀恶地");
        put("wooded_badlands", "繁茂恶地");
        put("meadow", "草甸");
        put("grove", "雪林");
        put("snowy_slopes", "积雪坡");
        put("jagged_peaks", "尖峭山峰");
        put("frozen_peaks", "冰冻山峰");
        put("stony_peaks", "石峰");
        put("cherry_grove", "樱花树林");
        put("mangrove_swamp", "红树林沼泽");
        put("pale_garden", "苍白之园");
        // 河流/海滩
        put("river", "河流");
        put("frozen_river", "冻河");
        put("beach", "沙滩");
        put("snowy_beach", "积雪沙滩");
        put("stone_shore", "石岸");
        // 洞穴
        put("dripstone_caves", "溶洞");
        put("lush_caves", "繁茂洞穴");
        put("deep_dark", "深暗之域");
        // 下界
        put("nether_wastes", "下界荒地");
        put("crimson_forest", "绯红森林");
        put("warped_forest", "诡异森林");
        put("soulsand_valley", "灵魂沙谷");
        put("basalt_deltas", "玄武岩三角洲");
        // 末地
        put("the_end", "末地");
        put("end_highlands", "末地高地");
        put("end_midlands", "末地内陆");
        put("end_barrens", "末地荒地");
        put("small_end_islands", "末地小岛");
    }

    private static void put(String en, String zh) {
        ZH.put(en, zh);
    }

    /** 英文名 → 显示名（中文优先，未知回退英文；null → null）。 */
    public static String displayName(String en) {
        if (en == null) {
            return null;
        }
        String zh = ZH.get(en);
        return zh != null ? zh : en;
    }

    private BiomeNames() {
    }
}
