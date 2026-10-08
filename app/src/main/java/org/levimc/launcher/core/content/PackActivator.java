package org.levimc.launcher.core.content;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * v0.1.0：资源包/行为包「激活到存档」——不复制包文件，只把包条目写入
 * 存档目录的 world_resource_packs.json / world_behavior_packs.json。
 * 兼容 MC 两种格式：数组 [...条目] 与对象 {"active":[...]}。
 */
public final class PackActivator {

    private static final String TAG = "PackActivator";

    private PackActivator() {
    }

    /** 结果：OK=已激活，ALREADY=存档已启用该包，FAILED=失败。 */
    public enum Result { OK, ALREADY, FAILED }

    /**
     * 激活包到指定存档。
     *
     * @param worldDir   存档目录（含 level.dat 的目录）
     * @param packId     包 uuid
     * @param versionStr 包版本字符串（如 "1.0.0"，解析失败回退 [1,0,0]）
     * @param behavior   true=行为包（world_behavior_packs.json），false=资源包
     */
    public static Result activate(File worldDir, String packId, String versionStr,
                                  boolean behavior) {
        if (worldDir == null || packId == null || packId.isEmpty()) {
            return Result.FAILED;
        }
        File jsonFile = new File(worldDir,
                behavior ? "world_behavior_packs.json" : "world_resource_packs.json");
        try {
            JSONObject rootObj = null;   // 对象格式 {"active":[...]}
            JSONArray target = null;     // 数组格式 [...] 或对象里的 active
            if (jsonFile.isFile()) {
                String content = readAll(jsonFile).trim();
                if (!content.isEmpty()) {
                    if (content.startsWith("{")) {
                        rootObj = new JSONObject(content);
                        target = rootObj.optJSONArray("active");
                        if (target == null) {
                            target = new JSONArray();
                            rootObj.put("active", target);
                        }
                    } else {
                        target = new JSONArray(content);
                    }
                }
            }
            if (target == null) {
                target = new JSONArray();
            }
            // 去重：同 pack_id 已在列表则跳过
            for (int i = 0; i < target.length(); i++) {
                JSONObject o = target.optJSONObject(i);
                if (o != null && packId.equals(o.optString("pack_id", ""))) {
                    return Result.ALREADY;
                }
            }
            JSONObject entry = new JSONObject();
            entry.put("pack_id", packId);
            entry.put("version", parseVersion(versionStr));
            target.put(entry);

            // 写回：保持原格式（对象 or 数组）
            if (rootObj != null) {
                writeAll(jsonFile, rootObj.toString(2));
            } else {
                writeAll(jsonFile, target.toString(2));
            }
            Log.i(TAG, "激活包 " + packId + " 到 " + jsonFile.getAbsolutePath());
            return Result.OK;
        } catch (Throwable t) {
            Log.w(TAG, "激活失败: " + t.getClass().getSimpleName(), t);
            return Result.FAILED;
        }
    }

    /** "1.0.0" / "1.20.51" → [1,0,0]；解析失败回退 [1,0,0]。 */
    private static JSONArray parseVersion(String v) {
        JSONArray arr = new JSONArray();
        String[] parts = v == null ? new String[0] : v.split("\\.");
        int major = 1, minor = 0, patch = 0;
        try {
            if (parts.length > 0) major = Integer.parseInt(parts[0].trim());
            if (parts.length > 1) minor = Integer.parseInt(parts[1].trim());
            if (parts.length > 2) patch = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ignored) {
        }
        arr.put(major);
        arr.put(minor);
        arr.put(patch);
        return arr;
    }

    private static String readAll(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 1024 * 1024)];
            int off = 0;
            int n;
            while (off < buf.length && (n = fis.read(buf, off, buf.length - off)) > 0) {
                off += n;
            }
            return new String(buf, 0, off, StandardCharsets.UTF_8);
        }
    }

    private static void writeAll(File f, String s) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
