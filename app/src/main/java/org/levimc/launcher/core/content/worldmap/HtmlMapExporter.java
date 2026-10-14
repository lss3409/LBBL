package org.levimc.launcher.core.content.worldmap;

/**
 * v0.4.48：HTML 地图导出（全新实现，替代旧 Leaflet 版——旧版导出
 * 后在浏览器打不开）。
 *
 * 方案：WorldMap 数据拼整图 → PNG → base64 内嵌单文件 HTML，
 * 查看器为手写 Canvas 拖拽/缩放（零外部依赖、离线可用、任意浏览器）。
 * 超大地图自动降采样到 4096 上限。
 */
public final class HtmlMapExporter {

    /** 拼图边长上限（超限按 2 的倍数降采样）。 */
    private static final int MAX_DIM = 4096;

    private HtmlMapExporter() {
    }

    /** 导出单文件 HTML。points 每项 = {名称, x, z, 颜色#RRGGBB}（block 坐标）。 */
    public static java.io.File export(WorldMap map, java.io.File outDir, String fileName,
                                      String title, long seed, String version,
                                      int playerX, int playerZ, int spawnX, int spawnZ,
                                      java.util.List<String[]> points) throws Exception {
        if (map == null || map.width <= 0 || map.height <= 0) {
            throw new Exception("no map data");
        }
        int[] full = composePixels(map);
        if (full == null) {
            throw new Exception("no rendered chunks");
        }
        int w = full[0], h = full[1];
        int[] px = new int[w * h];
        System.arraycopy(full, 2, px, 0, w * h);
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(
                w, h, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.setPixels(px, 0, w, 0, 0, w, h);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(w * h);
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
        bmp.recycle();
        String b64 = android.util.Base64.encodeToString(
                baos.toByteArray(), android.util.Base64.NO_WRAP);

        String html = buildHtml(title, seed, version, w, h,
                map.minBlockX, map.minBlockZ, playerX, playerZ, spawnX, spawnZ, points, b64);

        java.io.File out = new java.io.File(outDir, fileName);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            fos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out;
    }

    /** 拼整图像素：小世界直接取整图数组；大世界按 16×16 chunk 填充。
     *  返回 {width, height, pixels...}（降采样后）。 */
    private static int[] composePixels(WorldMap map) {
        int scale = 1;
        while (map.width / scale > MAX_DIM || map.height / scale > MAX_DIM) {
            scale <<= 1;
        }
        int w = Math.max(1, map.width / scale);
        int h = Math.max(1, map.height / scale);
        int[] out = new int[w * h + 2];
        out[0] = w;
        out[1] = h;

        if (map.colors != null) {
            // 小世界：整图数组逐像素降采样（取左上）
            int src = map.colors;
            int sw = map.width;
            for (int z = 0; z < h; z++) {
                int srcZ = z * scale * sw;
                int dst = z * w + 2;
                for (int x = 0; x < w; x++) {
                    int i = srcZ + x * scale;
                    out[dst + x] = i < src.length ? src[i] : 0;
                }
            }
            return out;
        }

        if (map.chunkColors == null || map.chunkColors.isEmpty()) {
            return null;
        }
        // 大世界：遍历 chunk 填充（像素色缓存在 16×16 int[] 中）
        for (java.util.Map.Entry<Long, int[]> e : map.chunkColors.entrySet()) {
            int cx = (int) (e.getKey() >> 32);
            int cz = (int) (long) e.getKey();
            int[] chunk = e.getValue();
            if (chunk == null || chunk.length < 256) {
                continue;
            }
            int baseBx = cx * 16;
            int baseBz = cz * 16;
            // 该 chunk 与地图 bounds 的交集（按 block 计）
            int sx = Math.max(0, map.minBlockX - baseBx);
            int sz = Math.max(0, map.minBlockZ - baseBz);
            int ex = Math.min(16, map.minBlockX + map.width - baseBx);
            int ez = Math.min(16, map.minBlockZ + map.height - baseBz);
            if (sx >= ex || sz >= ez) {
                continue;
            }
            for (int lz = sz; lz < ez; lz++) {
                int bz = baseBz + lz;
                int oz = (bz - map.minBlockZ) / scale;
                if (oz < 0 || oz >= h) {
                    continue;
                }
                int chunkRow = lz * 16;
                int dstRow = oz * w + 2;
                for (int lx = sx; lx < ex; lx++) {
                    // 降采样：只取 scale 对齐的像素（scale=1 全填）
                    if (lx % scale != 0) {
                        continue;
                    }
                    int bx = baseBx + lx;
                    int ox = (bx - map.minBlockX) / scale;
                    if (ox < 0 || ox >= w) {
                        continue;
                    }
                    out[dstRow + ox] = chunk[chunkRow + lx];
                }
            }
        }
        return out;
    }

    private static String buildHtml(String title, long seed, String version,
                                    int w, int h, int minX, int minZ,
                                    int playerX, int playerZ, int spawnX, int spawnZ,
                                    java.util.List<String[]> points, String b64) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">\n")
          .append("<title>LBBL 地图 - ").append(esc(title)).append("</title>\n")
          .append("<style>\n")
          .append("*{margin:0;padding:0;box-sizing:border-box}\n")
          .append("html,body{width:100%;height:100%;overflow:hidden;background:#1a1d21;")
          .append("font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#dde}\n")
          .append("#cv{display:block;cursor:grab;touch-action:none}\n")
          .append("#cv.drag{cursor:grabbing}\n")
          .append("#hud{position:fixed;left:10px;top:10px;background:rgba(20,22,26,.85);")
          .append("border-radius:10px;padding:8px 12px;font-size:12px;line-height:1.7;")
          .append("pointer-events:none;max-width:60vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
          .append("#legend{position:fixed;right:10px;top:10px;background:rgba(20,22,26,.85);")
          .append("border-radius:10px;padding:10px 12px;font-size:12px;max-height:70vh;")
          .append("overflow-y:auto;max-width:40vw}\n")
          .append("#legend .t{font-weight:700;margin-bottom:4px;color:#fff}\n")
          .append("#legend .p{display:flex;align-items:center;gap:6px;margin:2px 0;white-space:nowrap}\n")
          .append("#legend .dot{width:9px;height:9px;border-radius:50%;flex:none}\n")
          .append("#tip{position:fixed;bottom:10px;left:50%;transform:translateX(-50%);")
          .append("background:rgba(20,22,26,.85);border-radius:999px;padding:6px 16px;")
          .append("font-size:11px;color:#9ab;pointer-events:none}\n")
          .append("</style>\n</head>\n<body>\n")
          .append("<canvas id=\"cv\"></canvas>\n")
          .append("<div id=\"hud\"></div>\n")
          .append("<div id=\"legend\"><div class=\"t\">标点</div></div>\n")
          .append("<div id=\"tip\">拖拽移动 · 滚轮/双指缩放 · 双击复位</div>\n")
          .append("<script>\n(function(){\n");
        sb.append("var IMG='data:image/png;base64,").append(b64).append("';\n");
        sb.append("var MAP_W=").append(w).append(",MAP_H=").append(h)
          .append(",MIN_X=").append(minX).append(",MIN_Z=").append(minZ).append(";\n");
        sb.append("var PLAYER_X=").append(playerX).append(",PLAYER_Z=").append(playerZ)
          .append(",SPAWN_X=").append(spawnX).append(",SPAWN_Z=").append(spawnZ).append(";\n");
        sb.append("var INFO='").append(esc(title))
          .append(" · v").append(esc(version))
          .append(" · 种子 ").append(seed).append(" · 尺寸 ").append(w).append('x').append(h)
          .append(" 方块';\n");
        sb.append("var POINTS=[");
        if (points != null) {
            boolean first = true;
            for (String[] p : points) {
                if (p == null || p.length < 4) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append("{n:'").append(esc(p[0]))
                  .append("',x:").append(p[1])
                  .append(",z:").append(p[2])
                  .append(",c:'").append(p[3].startsWith("#") ? p[3] : "#" + p[3]).append("'}");
            }
        }
        sb.append("];\n");
        sb.append(
            "var cv=document.getElementById('cv'),ctx=cv.getContext('2d');\n"
          + "var img=new Image();img.onload=function(){fit();draw();};img.src=IMG;\n"
          + "var scale=1,tx=0,ty=0,dragging=false,lx=0,ly=0;\n"
          + "var dpr=window.devicePixelRatio||1;\n"
          + "function resize(){cv.width=innerWidth*dpr;cv.height=innerHeight*dpr;"
          + "cv.style.width=innerWidth+'px';cv.style.height=innerHeight+'px';}\n"
          + "function fit(){var s=Math.min(innerWidth/MAP_W,innerHeight/MAP_H)*0.92;"
          + "scale=s;tx=(innerWidth-MAP_W*s)/2;ty=(innerHeight-MAP_H*s)/2;}\n"
          + "function blockAt(mx,my){return{x:Math.floor((mx-tx)/scale)+MIN_X,"
          + "z:Math.floor((my-ty)/scale)+MIN_Z};}\n"
          + "function draw(){\n"
          + "  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,cv.width,cv.height);\n"
          + "  ctx.translate(tx*dpr,ty*dpr);ctx.scale(scale*dpr,scale*dpr);\n"
          + "  ctx.imageSmoothingEnabled=scale<2;\n"
          + "  ctx.drawImage(img,0,0,MAP_W,MAP_H);\n"
          + "  var r=Math.max(3,1.2/scale);\n"
          + "  if(SPAWN_X>=0){ctx.fillStyle='#ffd54f';ctx.beginPath();"
          + "ctx.arc(SPAWN_X-MIN_X,SPAWN_Z-MIN_Z,r*1.7,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#5d4037';ctx.lineWidth=r*0.5;ctx.stroke();}\n"
          + "  if(PLAYER_X>=0){ctx.fillStyle='#4caf50';ctx.beginPath();"
          + "ctx.arc(PLAYER_X-MIN_X,PLAYER_Z-MIN_Z,r*1.4,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#fff';ctx.lineWidth=r*0.4;ctx.stroke();}\n"
          + "  for(var i=0;i<POINTS.length;i++){var p=POINTS[i];"
          + "ctx.fillStyle=p.c;ctx.beginPath();"
          + "ctx.arc(p.x-MIN_X,p.z-MIN_Z,r,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='rgba(0,0,0,.55)';ctx.lineWidth=r*0.35;ctx.stroke();}\n"
          + "}\n"
          + "cv.addEventListener('pointerdown',function(e){dragging=true;lx=e.clientX;ly=e.clientY;"
          + "cv.classList.add('drag');});\n"
          + "window.addEventListener('pointermove',function(e){"
          + "if(dragging){tx+=e.clientX-lx;ty+=e.clientY-ly;lx=e.clientX;ly=e.clientY;draw();}"
          + "var b=blockAt(e.clientX,e.clientY);"
          + "document.getElementById('hud').textContent=INFO+' · X '+b.x+' Z '+b.z;"
          + "});\n"
          + "window.addEventListener('pointerup',function(){dragging=false;"
          + "cv.classList.remove('drag');});\n"
          + "cv.addEventListener('wheel',function(e){e.preventDefault();"
          + "var f=Math.exp(-e.deltaY*0.0015);var ns=Math.min(64,Math.max(0.05,scale*f));"
          + "var mx=e.clientX,my=e.clientY;"
          + "tx=mx-(mx-tx)*(ns/scale);ty=my-(my-ty)*(ns/scale);scale=ns;draw();"
          + "},{passive:false});\n"
          + "cv.addEventListener('dblclick',function(){fit();draw();});\n"
          + "window.addEventListener('resize',function(){resize();draw();});\n"
          + "var lg=document.getElementById('legend');\n"
          + "if(POINTS.length){var s='<div class=\"t\">标点 ('+POINTS.length+')</div>';"
          + "for(var i=0;i<POINTS.length;i++){s+='<div class=\"p\"><span class=\"dot\" style=\"background:'"
          + "+(POINTS[i].c)+'\"></span>'+POINTS[i].n+'</div>';}lg.innerHTML=s;}\n"
          + "else{lg.style.display='none';}\n"
          + "draw();\n"
          + "})();\n</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "\\'").replace("\"", "&quot;");
    }
}
