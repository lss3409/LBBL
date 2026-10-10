package org.levimc.launcher.core.curseforge.models;

import java.util.List;

/** v0.4.8：GET /v1/categories?gameId=... 响应（GDK 版同款 API）。
 *  官方前端两级结构：isClass=大类（资源类型），其余为分类，
 *  分类跟随所选大类联动。 */
public class CategoriesResponse {
    public List<CategoryData> data;

    public static class CategoryData {
        public int id;
        public int classId;
        public String name;
        public boolean isClass;
        public int displayIndex;
    }
}
