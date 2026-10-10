package org.levimc.launcher.core.curseforge.models;

import java.util.List;

/** v0.4.8：GET /v1/games/{gameId}/versions 响应（GDK 版同款 API）。 */
public class GameVersionsResponse {
    public List<GameVersion> data;

    public static class GameVersion {
        public String name;
    }
}
