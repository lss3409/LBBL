package org.levimc.launcher.core.content.worldmap;

/**
 * v0.4.49：HTML 地图导出重写为纯代码渲染（用户要求）——
 * 不再内嵌 PNG 图片，而是把像素色块做「调色板 + 游程编码（RLE）」
 * 打包成二进制数据，HTML 内手写 Canvas 解码绘制。
 *
 * 收益：缩放上限极高（每方块可放大到整屏）、放大用最近邻采样
 * 色块锐利不模糊、文件不受图片编码限制（色块地图 RLE 压缩率更高）。
 */
public final class HtmlMapExporter {

    /** v0.4.73：HTML 导出边长上限放宽到 8192（浏览器可承载，导出清晰度
     *  不再被 4096 截断；OOM 时回退 4096）。 */
    private static final int HTML_MAX_DIM = 8192;
    private static final int HTML_FALLBACK_DIM = MapImageComposer.MAX_DIM;

    private HtmlMapExporter() {
    }

    /** 导出单文件 HTML。points 每项 = {名称, x, z, 颜色#RRGGBB}（block 坐标）。 */
    public static java.io.File export(WorldMapRenderer.WorldMap map, java.io.File outDir, String fileName,
                                      String title, long seed, String version,
                                      int playerX, int playerZ, int spawnX, int spawnZ,
                                      int deathX, int deathZ,
                                      java.util.List<String[]> points) throws Exception {
        if (map == null || map.width <= 0 || map.height <= 0) {
            throw new Exception("no map data");
        }
        int w;
        int h;
        String data;
        int maxSide = Math.max(map.width, map.height);
        if (maxSide <= HTML_MAX_DIM) {
            // 快速路径：≤8192 走 compose（一次全量数组）
            int[] full = null;
            try {
                full = MapImageComposer.compose(map, HTML_MAX_DIM);
            } catch (OutOfMemoryError oom) {
                // v0.4.73：低内存设备回退 4096（清晰度让位于可用性）
                full = MapImageComposer.compose(map, HTML_FALLBACK_DIM);
            }
            if (full == null) {
                throw new Exception("no rendered chunks");
            }
            w = full[0];
            h = full[1];
            // v0.4.73：不再拷贝一份像素数组（8192² 时再拷一份=多占 268MB），
            // 直接对 full 偏移编码
            data = encodeRle(full, 2, w * h);
        } else {
            // v0.4.91：超大地图（>8192）瓦片化全分辨率导出——不再降采样
            // （v0.4.88 的 16384 上限=「还能再清晰一点」的残留）。地图切成
            // 2048×2048 瓦片（每瓦片独立 RLE+调色板），JS 端只解码视口
            // 内瓦片（LRU 8 张）——任意大世界都一比一（游戏内同分辨率）
            int scale = 1;
            w = Math.max(1, map.width / scale);
            h = Math.max(1, map.height / scale);
            String[] tiles = encodeTiles(map, w, h, scale);
            String html = buildTiledHtml(title, seed, version, w, h,
                    map.minBlockX, map.minBlockZ, playerX, playerZ, spawnX, spawnZ,
                    deathX, deathZ, points, tiles);
            java.io.File out = new java.io.File(outDir, fileName);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
                fos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return out;
        }
        String html = buildHtml(title, seed, version, w, h,
                map.minBlockX, map.minBlockZ, playerX, playerZ, spawnX, spawnZ,
                deathX, deathZ, points, data);

        java.io.File out = new java.io.File(outDir, fileName);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            fos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out;
    }

    /** v0.4.91：瓦片切分编码——每瓦片 2048×2048 独立 RLE（palette 独立，
     *  内存=瓦片块+一行）。返回行优先的 base64 数组。 */
    private static final int TILE_SIZE = 2048;

    private static String[] encodeTiles(WorldMapRenderer.WorldMap map,
                                        int w, int h, int scale) throws java.io.IOException {
        int cols = (w + TILE_SIZE - 1) / TILE_SIZE;
        int rows = (h + TILE_SIZE - 1) / TILE_SIZE;
        String[] out = new String[cols * rows];
        int[] fullRow = new int[w];
        int[] block = new int[TILE_SIZE * TILE_SIZE];
        for (int ty = 0; ty < rows; ty++) {
            int th = Math.min(TILE_SIZE, h - ty * TILE_SIZE);
            for (int tx = 0; tx < cols; tx++) {
                int tw = Math.min(TILE_SIZE, w - tx * TILE_SIZE);
                for (int ly = 0; ly < th; ly++) {
                    writeRow(map, fullRow, ty * TILE_SIZE + ly, w, scale);
                    System.arraycopy(fullRow, tx * TILE_SIZE, block, ly * tw, tw);
                }
                out[ty * cols + tx] = encodeRle(block, 0, tw * th);
            }
        }
        return out;
    }

    /** v0.4.88：超大地图流式全分辨率编码——逐行采样像素（内存=一行），
     *  runs 边扫边写（相邻同色走 lastColor 快速路径，游程地图命中率
     *  极高），最后拼 palette 头 + runs → base64。 */
    private static String encodeRleStreaming(WorldMapRenderer.WorldMap map,
                                             int w, int h, int scale) throws java.io.IOException {
        java.util.LinkedHashMap<Integer, Integer> palette = new java.util.LinkedHashMap<>();
        java.io.ByteArrayOutputStream runs = new java.io.ByteArrayOutputStream(
                Math.min(w * h, 1 << 22));
        int[] row = new int[w];
        int lastC = 0;
        int lastId = -1;
        int runId = -1;
        int runLen = 0;
        for (int z = 0; z < h; z++) {
            writeRow(map, row, z, w, scale);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                int id;
                if (c == lastC && lastId >= 0) {
                    id = lastId;
                } else {
                    Integer g = palette.get(c);
                    if (g == null) {
                        g = palette.size();
                        palette.put(c, g);
                    }
                    id = g;
                    lastC = c;
                    lastId = id;
                }
                if (id == runId && runLen < 65535) {
                    runLen++;
                } else {
                    if (runLen > 0) {
                        writeShortLE(runs, runId);
                        writeShortLE(runs, runLen);
                    }
                    runId = id;
                    runLen = 1;
                }
            }
        }
        if (runLen > 0) {
            writeShortLE(runs, runId);
            writeShortLE(runs, runLen);
        }
        // 拼：paletteCount(2B) + RGB×n(3B) + runs
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(
                runs.size() + palette.size() * 3 + 16);
        writeShortLE(bos, palette.size());
        for (int c : palette.keySet()) {
            bos.write((c >> 16) & 0xFF);
            bos.write((c >> 8) & 0xFF);
            bos.write(c & 0xFF);
        }
        runs.writeTo(bos);
        return android.util.Base64.encodeToString(bos.toByteArray(),
                android.util.Base64.NO_WRAP);
    }

    /** 生成第 z 行像素（缩放坐标）：小世界直读 colors；大世界按 chunk
     *  行缓存遍历 chunkColors（每行只查 w×scale/16 次 HashMap）。 */
    private static void writeRow(WorldMapRenderer.WorldMap map, int[] row,
                                 int z, int w, int scale) {
        if (map.colors != null) {
            int srcZ = z * scale * map.width;
            for (int x = 0; x < w; x++) {
                int i = srcZ + x * scale;
                row[x] = i < map.colors.length ? map.colors[i] : 0;
            }
            return;
        }
        if (map.chunkColors == null) {
            java.util.Arrays.fill(row, 0);
            return;
        }
        int cz = Math.floorDiv(map.minBlockZ + z * scale, 16);
        int lz = Math.floorMod(map.minBlockZ + z * scale, 16);
        int curCx = Integer.MIN_VALUE;
        int[] chunk = null;
        for (int x = 0; x < w; x++) {
            int bx = map.minBlockX + x * scale;
            int cx = Math.floorDiv(bx, 16);
            if (cx != curCx) {
                curCx = cx;
                chunk = map.chunkColors.get(((long) cx << 32) | (cz & 0xFFFFFFFFL));
            }
            int lx = Math.floorMod(bx, 16);
            row[x] = chunk != null && chunk.length >= 256 ? chunk[lz * 16 + lx] : 0;
        }
    }

    /** 调色板 + 游程编码：paletteCount(2B) + RGB×n(3B each) +
     *  runs(index 2B + count 2B)… → base64。
     *  v0.4.73：流式两遍编码，不再建 index 数组（省一份像素数组内存）。 */
    private static String encodeRle(int[] px, int offset, int length) throws java.io.IOException {
        java.util.LinkedHashMap<Integer, Integer> palette = new java.util.LinkedHashMap<>();
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            int c = px[i];
            if (!palette.containsKey(c)) {
                palette.put(c, palette.size());
            }
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(length / 4 + 256);
        // 小端写（JS 端 DataView.getUint16(..., true) 小端读——大端写会
        // 字节序错位，HTML 黑屏根因）
        writeShortLE(bos, palette.size());
        for (int c : palette.keySet()) {
            bos.write((c >> 16) & 0xFF);
            bos.write((c >> 8) & 0xFF);
            bos.write(c & 0xFF);
        }
        for (int i = offset; i < end; ) {
            int c = px[i];
            int j = i + 1;
            while (j < end && px[j] == c && j - i < 65535) {
                j++;
            }
            writeShortLE(bos, palette.get(c));
            writeShortLE(bos, j - i);
            i = j;
        }
        return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private static String buildTiledHtml(String title, long seed, String version,
                                         int w, int h, int minX, int minZ,
                                         int playerX, int playerZ, int spawnX, int spawnZ,
                                         int deathX, int deathZ,
                                         java.util.List<String[]> points, String[] tiles) {
        StringBuilder sb = new StringBuilder(128 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">\n")
          .append("<title>LBBL 地图 - ").append(esc(title)).append("</title>\n")
          .append("<style>\n")
          .append("*{margin:0;padding:0;box-sizing:border-box}\n")
          .append("html,body{width:100%;height:100%;overflow:hidden;background:#14161a;")
          .append("font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#dde}\n")
          .append("#cv{display:block;cursor:grab;touch-action:none}\n")
          .append("#cv.drag{cursor:grabbing}\n")
          .append("#hud{position:fixed;left:10px;top:10px;background:rgba(20,22,26,.85);")
          .append("border-radius:10px;padding:8px 12px;font-size:12px;line-height:1.8;")
          .append("pointer-events:none;max-width:70vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
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
        sb.append("var MAP_W=").append(w).append(",MAP_H=").append(h)
          .append(",MIN_X=").append(minX).append(",MIN_Z=").append(minZ).append(";\n");
        sb.append("var PLAYER_X=").append(playerX).append(",PLAYER_Z=").append(playerZ)
          .append(",SPAWN_X=").append(spawnX).append(",SPAWN_Z=").append(spawnZ)
          .append(",DEATH_X=").append(deathX).append(",DEATH_Z=").append(deathZ).append(";\n");
        sb.append("var TITLE='").append(esc(title))
          .append("',VER='").append(esc(version))
          .append("',SEED=").append(seed).append(";\n");
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
        // 瓦片数据（行优先 base64）
        sb.append("var TDATA=[");
        for (int i = 0; i < tiles.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("'").append(tiles[i]).append("'");
        }
        sb.append("];\n");
        sb.append(
            "var cv=document.getElementById('cv'),ctx=cv.getContext('2d');\n"
          + "var scale=1,tx=0,ty=0,dragging=false,lx=0,ly=0;\n"
          + "var dpr=window.devicePixelRatio||1;\n"
          + "var MIN_S=0.02,MAX_S=2048;\n"
          + "var TS=2048,COL=Math.ceil(MAP_W/TS),ROW=Math.ceil(MAP_H/TS);\n"
          + "var tcache={},torder=[];\n"
          + "function getTile(tx0,ty0){\n"
          + "  var key=tx0+','+ty0;\n"
          + "  if(tcache[key])return tcache[key];\n"
          + "  var d=TDATA[ty0*COL+tx0];\n"
          + "  var tw=Math.min(TS,MAP_W-tx0*TS),th=Math.min(TS,MAP_H-ty0*TS);\n"
          + "  var bin=atob(d),u8=new Uint8Array(bin.length),i;\n"
          + "  for(i=0;i<bin.length;i++)u8[i]=bin.charCodeAt(i);\n"
          + "  var dv=new DataView(u8.buffer);\n"
          + "  var n=dv.getUint16(0,true),pal32=[],o=2;\n"
          + "  for(i=0;i<n;i++){var a=(u8[o]||u8[o+1]||u8[o+2])?255:0;"
          + "pal32.push((a<<24)|(u8[o+1]<<16)|(u8[o+2]<<8)|u8[o]);o+=3;}\n"
          + "  var tcv=document.createElement('canvas');tcv.width=tw;tcv.height=th;\n"
          + "  var tc2=tcv.getContext('2d');\n"
          + "  var img=tc2.createImageData(tw,th),pd32=new Uint32Array(img.data.buffer),p=0;\n"
          + "  var total=tw*th;\n"
          + "  while(p<total){\n"
          + "    var id=dv.getUint16(o,true);o+=2;\n"
          + "    var cnt=dv.getUint16(o,true);o+=2;\n"
          + "    var v=pal32[id];\n"
          + "    for(var k=0;k<cnt;k++)pd32[p+k]=v;\n"
          + "    p+=cnt;\n"
          + "  }\n"
          + "  tc2.putImageData(img,0,0);\n"
          + "  // v0.4.92：瓦片 mip 链（数据不降分辨率——解码后生成 1/2、1/4\n"
          + "  // 显示副本；缩小时画小图=流畅，放大时画原图=一比一）\n"
          + "  var m2=document.createElement('canvas');\n"
          + "  m2.width=Math.max(1,Math.ceil(tw/2));m2.height=Math.max(1,Math.ceil(th/2));\n"
          + "  var mc2=m2.getContext('2d');mc2.imageSmoothingEnabled=true;\n"
          + "  mc2.drawImage(tcv,0,0,m2.width,m2.height);\n"
          + "  var m4=document.createElement('canvas');\n"
          + "  m4.width=Math.max(1,Math.ceil(tw/4));m4.height=Math.max(1,Math.ceil(th/4));\n"
          + "  var mc4=m4.getContext('2d');mc4.imageSmoothingEnabled=true;\n"
          + "  mc4.drawImage(m2,0,0,m4.width,m4.height);\n"
          + "  var entry={c:tcv,m2:m2,m4:m4};\n"
          + "  tcache[key]=entry;torder.push(key);\n"
          + "  while(torder.length>6){var old=torder.shift();delete tcache[old];}\n"
          + "  return entry;\n"
          + "}\n"
          + "function resize(){cv.width=innerWidth*dpr;cv.height=innerHeight*dpr;"
          + "cv.style.width=innerWidth+'px';cv.style.height=innerHeight+'px';}\n"
          + "function fit(){var s=Math.min(innerWidth/MAP_W,innerHeight/MAP_H)*0.92;"
          + "scale=s;tx=(innerWidth-MAP_W*s)/2;ty=(innerHeight-MAP_H*s)/2;}\n"
          + "function blockAt(mx,my){return{x:Math.floor((mx-tx)/scale)+MIN_X,"
          + "z:Math.floor((my-ty)/scale)+MIN_Z};}\n"
          + "function drawMap(){\n"
          + "  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,cv.width,cv.height);\n"
          + "  ctx.translate(tx*dpr,ty*dpr);ctx.scale(scale*dpr,scale*dpr);\n"
          + "  ctx.imageSmoothingEnabled=false;\n"
          + "  var x0=Math.max(0,(-tx)/scale),y0=Math.max(0,(-ty)/scale);\n"
          + "  var x1=Math.min(MAP_W,(cv.width/dpr-tx)/scale),y1=Math.min(MAP_H,(cv.height/dpr-ty)/scale);\n"
          + "  var bx0=Math.floor(x0/TS),bx1=Math.floor(x1/TS),by0=Math.floor(y0/TS),by1=Math.floor(y1/TS);\n"
          + "  // v0.4.92：按缩放选瓦片级（mip 金字塔，Leaflet/mapbox 同款思路）\n"
          + "  var mip=scale>=0.75?1: scale>=0.35?2:4;\n"
          + "  for(var by=by0;by<=by1;by++)for(var bx=bx0;bx<=bx1;bx++){\n"
          + "    var e=getTile(bx,by);\n"
          + "    var src=mip===1?e.c: mip===2?e.m2:e.m4;\n"
          + "    var tw=Math.min(TS,MAP_W-bx*TS),th=Math.min(TS,MAP_H-by*TS);\n"
          + "    ctx.drawImage(src,0,0,src.width,src.height,bx*TS,by*TS,tw,th);\n"
          + "  }\n"
          + "  var r=Math.max(3,1.2/scale);\n"
          + "  if(SPAWN_X>=0){ctx.fillStyle='#ffd54f';ctx.beginPath();"
          + "ctx.arc(SPAWN_X-MIN_X,SPAWN_Z-MIN_Z,r*1.7,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#5d4037';ctx.lineWidth=r*0.5;ctx.stroke();}\n"
          + "  if(PLAYER_X>=0){ctx.fillStyle='#4caf50';ctx.beginPath();"
          + "ctx.arc(PLAYER_X-MIN_X,PLAYER_Z-MIN_Z,r*1.4,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#fff';ctx.lineWidth=r*0.4;ctx.stroke();}\n"
          + "  if(DEATH_X>=0){ctx.fillStyle='#e53935';ctx.beginPath();"
          + "ctx.arc(DEATH_X-MIN_X,DEATH_Z-MIN_Z,r*1.3,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#fff';ctx.lineWidth=r*0.35;ctx.stroke();}\n"
          + "  for(var i=0;i<POINTS.length;i++){var p=POINTS[i];"
          + "ctx.fillStyle=p.c;ctx.beginPath();"
          + "ctx.arc(p.x-MIN_X,p.z-MIN_Z,r,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='rgba(0,0,0,.55)';ctx.lineWidth=r*0.35;ctx.stroke();}\n"
          + "}\n"
          + "// v0.4.93：合成帧快照（openlayers 同款）——全量绘制后存屏幕快照；\n"
          + "// 缩放动画期间只拉伸快照（1 次 drawImage），静止 140ms 才全量重绘。\n"
          + "// v0.4.94：修 drawPreview 坐标（tx/ty 是 CSS 像素，此前错乘 scale*dpr\n"
          + "// 导致放大时快照飞出屏幕=黑屏）；快照失效阈值 k>2/k<0.5 直接全量刷新；\n"
          + "// rAF 节流+代次守卫（过期预览不覆盖新全量画面）；缩小用双线性。\n"
          + "var snapC=null,snapT=null,snapTimer=null,snapGen=0;\n"
          + "function snapShot(){\n"
          + "  if(!snapC){snapC=document.createElement('canvas');snapC.width=cv.width;snapC.height=cv.height;}\n"
          + "  snapC.getContext('2d').drawImage(cv,0,0);\n"
          + "  snapT={tx:tx,ty:ty,scale:scale,gen:++snapGen};\n"
          + "}\n"
          + "function fullDraw(){drawMap();snapShot();}\n"
          + "function drawPreview(){\n"
          + "  if(!snapC||!snapT)return fullDraw();\n"
          + "  var k=scale/snapT.scale;\n"
          + "  if(k>2||k<0.5)return fullDraw();\n"
          + "  ctx.setTransform(1,0,0,1,0,0);\n"
          + "  ctx.fillStyle='#14161a';ctx.fillRect(0,0,cv.width,cv.height);\n"
          + "  var dx=(tx-snapT.tx*k)*dpr,dy=(ty-snapT.ty*k)*dpr;\n"
          + "  ctx.imageSmoothingEnabled=(k<1);\n"
          + "  ctx.drawImage(snapC,dx,dy,snapC.width*k,snapC.height*k);\n"
          + "}\n"
          + "var rafPending=0,rafGen=0;\n"
          + "function schedulePreview(){\n"
          + "  rafGen=snapGen;\n"
          + "  if(rafPending)return;\n"
          + "  rafPending=1;\n"
          + "  requestAnimationFrame(function(){rafPending=0;"
          + "if(rafGen===snapGen)drawPreview();});\n"
          + "}\n"
          + "function debounceFull(){\n"
          + "  if(snapTimer)clearTimeout(snapTimer);\n"
          + "  snapTimer=setTimeout(function(){snapTimer=null;fullDraw();},140);\n"
          + "}\n"
          + "function setHud(x,z){\n"
          + "  var s=TITLE+' · v'+VER+' · 种子 '+SEED+' · 尺寸 '+MAP_W+'x'+MAP_H+' 方块 · 缩放 '+Math.round(scale*100)/100+'x';\n"
          + "  if(x!==null)s+=' · 坐标 X '+x+' Z '+z;\n"
          + "  document.getElementById('hud').textContent=s;\n"
          + "}\n"
          + "function zoomAt(mx,my,ns){\n"
          + "  ns=Math.min(MAX_S,Math.max(MIN_S,ns));\n"
          + "  tx=mx-(mx-tx)*(ns/scale);ty=my-(my-ty)*(ns/scale);scale=ns;\n"
          + "  schedulePreview();debounceFull();\n"
          + "  setHud(null,0);\n"
          + "}\n"
          + "cv.addEventListener('pointerdown',function(e){dragging=true;lx=e.clientX;ly=e.clientY;"
          + "cv.classList.add('drag');});\n"
          + "window.addEventListener('pointermove',function(e){"
          + "if(dragging){tx+=e.clientX-lx;ty+=e.clientY-ly;lx=e.clientX;ly=e.clientY;fullDraw();}"
          + "var b=blockAt(e.clientX,e.clientY);setHud(b.x,b.z);"
          + "});\n"
          + "window.addEventListener('pointerup',function(){dragging=false;"
          + "cv.classList.remove('drag');});\n"
          + "cv.addEventListener('wheel',function(e){e.preventDefault();"
          + "var f=Math.exp(-e.deltaY*0.0015);zoomAt(e.clientX,e.clientY,scale*f);"
          + "},{passive:false});\n"
          + "var pinch0=0,pinchScale=1;\n"
          + "function tdist(t){var dx=t[0].clientX-t[1].clientX,dy=t[0].clientY-t[1].clientY;"
          + "return Math.sqrt(dx*dx+dy*dy);}\n"
          + "cv.addEventListener('touchstart',function(e){"
          + "if(e.touches.length==2){pinch0=tdist(e.touches);pinchScale=scale;dragging=false;}"
          + "},{passive:true});\n"
          + "cv.addEventListener('touchmove',function(e){"
          + "if(e.touches.length==2){e.preventDefault();"
          + "var d=tdist(e.touches);if(pinch0>0&&d>0){"
          + "var mx=(e.touches[0].clientX+e.touches[1].clientX)/2;"
          + "var my=(e.touches[0].clientY+e.touches[1].clientY)/2;"
          + "zoomAt(mx,my,pinchScale*d/pinch0);}}"
          + "},{passive:false});\n"
          + "cv.addEventListener('touchend',function(e){"
          + "if(e.touches.length<2){pinch0=0;}});\n"
          + "cv.addEventListener('dblclick',function(){fit();fullDraw();setHud(null,0);});\n"
          + "window.addEventListener('resize',function(){resize();fullDraw();});\n"
          + "var lg=document.getElementById('legend');\n"
          + "if(POINTS.length){var s='<div class=\"t\">标点 ('+POINTS.length+')</div>';"
          + "for(var i=0;i<POINTS.length;i++){s+='<div class=\"p\"><span class=\"dot\" style=\"background:'"
          + "+(POINTS[i].c)+'\"></span>'+POINTS[i].n+'</div>';}lg.innerHTML=s;}\n"
          + "else{lg.style.display='none';}\n"
          + "resize();fit();fullDraw();setHud(null,0);\n"
          + "})();\n</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static String buildHtml(String title, long seed, String version,
                                    int w, int h, int minX, int minZ,
                                    int playerX, int playerZ, int spawnX, int spawnZ,
                                    int deathX, int deathZ,
                                    java.util.List<String[]> points, String data) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
          .append("<meta charset=\"UTF-8\">\n")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,user-scalable=no\">\n")
          .append("<title>LBBL 地图 - ").append(esc(title)).append("</title>\n")
          .append("<style>\n")
          .append("*{margin:0;padding:0;box-sizing:border-box}\n")
          .append("html,body{width:100%;height:100%;overflow:hidden;background:#14161a;")
          .append("font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;color:#dde}\n")
          .append("#cv{display:block;cursor:grab;touch-action:none}\n")
          .append("#cv.drag{cursor:grabbing}\n")
          .append("#hud{position:fixed;left:10px;top:10px;background:rgba(20,22,26,.85);")
          .append("border-radius:10px;padding:8px 12px;font-size:12px;line-height:1.8;")
          .append("pointer-events:none;max-width:70vw;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}\n")
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
        sb.append("var DATA='").append(data).append("';\n");
        sb.append("var MAP_W=").append(w).append(",MAP_H=").append(h)
          .append(",MIN_X=").append(minX).append(",MIN_Z=").append(minZ).append(";\n");
        sb.append("var PLAYER_X=").append(playerX).append(",PLAYER_Z=").append(playerZ)
          .append(",SPAWN_X=").append(spawnX).append(",SPAWN_Z=").append(spawnZ)
          .append(",DEATH_X=").append(deathX).append(",DEATH_Z=").append(deathZ).append(";\n");
        sb.append("var TITLE='").append(esc(title))
          .append("',VER='").append(esc(version))
          .append("',SEED=").append(seed).append(";\n");
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
          + "var off=document.createElement('canvas'),octx;\n"
          + "var scale=1,tx=0,ty=0,dragging=false,lx=0,ly=0;\n"
          + "var dpr=window.devicePixelRatio||1;\n"
          + "var MIN_S=0.02,MAX_S=2048;\n"
          + "function resize(){cv.width=innerWidth*dpr;cv.height=innerHeight*dpr;"
          + "cv.style.width=innerWidth+'px';cv.style.height=innerHeight+'px';}\n"
          + "function fit(){var s=Math.min(innerWidth/MAP_W,innerHeight/MAP_H)*0.92;"
          + "scale=s;tx=(innerWidth-MAP_W*s)/2;ty=(innerHeight-MAP_H*s)/2;}\n"
          + "function blockAt(mx,my){return{x:Math.floor((mx-tx)/scale)+MIN_X,"
          + "z:Math.floor((my-ty)/scale)+MIN_Z};}\n"
          // ---- 解码 RLE ----
          + "function decodeRle(){\n"
          + "  var bin=atob(DATA),u8=new Uint8Array(bin.length),i;\n"
          + "  for(i=0;i<bin.length;i++)u8[i]=bin.charCodeAt(i);\n"
          + "  var dv=new DataView(u8.buffer);\n"
          + "  var n=dv.getUint16(0,true),pal=[],o=2,k;\n"
          + "  for(i=0;i<n;i++){pal.push([u8[o],u8[o+1],u8[o+2]]);o+=3;}\n"
          + "  off.width=MAP_W;off.height=MAP_H;octx=off.getContext('2d');\n"
          + "  var img=octx.createImageData(MAP_W,MAP_H),d=img.data,p=0;\n"
          + "  var total=MAP_W*MAP_H;\n"
          + "  while(p<total){\n"
          + "    var idx=dv.getUint16(o,true);o+=2;\n"
          + "    var cnt=dv.getUint16(o,true);o+=2;\n"
          + "    var c=pal[idx]||[0,0,0];\n"
          + "    var a=(c[0]===0&&c[1]===0&&c[2]===0)?0:255;\n"
          + "    for(k=0;k<cnt;k++){var q=(p+k)*4;d[q]=c[0];d[q+1]=c[1];d[q+2]=c[2];d[q+3]=a;}\n"
          + "    p+=cnt;\n"
          + "  }\n"
          + "  octx.putImageData(img,0,0);\n"
          + "}\n"
          + "function draw(){\n"
          + "  ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,cv.width,cv.height);\n"
          + "  ctx.translate(tx*dpr,ty*dpr);ctx.scale(scale*dpr,scale*dpr);\n"
          + "  ctx.imageSmoothingEnabled=false;\n"
          + "  ctx.drawImage(off,0,0,MAP_W,MAP_H);\n"
          + "  var r=Math.max(3,1.2/scale);\n"
          + "  if(SPAWN_X>=0){ctx.fillStyle='#ffd54f';ctx.beginPath();"
          + "ctx.arc(SPAWN_X-MIN_X,SPAWN_Z-MIN_Z,r*1.7,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#5d4037';ctx.lineWidth=r*0.5;ctx.stroke();}\n"
          + "  if(PLAYER_X>=0){ctx.fillStyle='#4caf50';ctx.beginPath();"
          + "ctx.arc(PLAYER_X-MIN_X,PLAYER_Z-MIN_Z,r*1.4,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#fff';ctx.lineWidth=r*0.4;ctx.stroke();}\n"
          + "  if(DEATH_X>=0){ctx.fillStyle='#e53935';ctx.beginPath();"
          + "ctx.arc(DEATH_X-MIN_X,DEATH_Z-MIN_Z,r*1.3,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='#fff';ctx.lineWidth=r*0.35;ctx.stroke();}\n"
          + "  for(var i=0;i<POINTS.length;i++){var p=POINTS[i];"
          + "ctx.fillStyle=p.c;ctx.beginPath();"
          + "ctx.arc(p.x-MIN_X,p.z-MIN_Z,r,0,6.283);ctx.fill();"
          + "ctx.strokeStyle='rgba(0,0,0,.55)';ctx.lineWidth=r*0.35;ctx.stroke();}\n"
          + "}\n"
          + "function setHud(x,z){\n"
          + "  var s=TITLE+' · v'+VER+' · 种子 '+SEED+' · 尺寸 '+MAP_W+'x'+MAP_H+' 方块 · 缩放 '+Math.round(scale*100)/100+'x';\n"
          + "  if(x!==null)s+=' · 坐标 X '+x+' Z '+z;\n"
          + "  document.getElementById('hud').textContent=s;\n"
          + "}\n"
          + "function zoomAt(mx,my,ns){\n"
          + "  ns=Math.min(MAX_S,Math.max(MIN_S,ns));\n"
          + "  tx=mx-(mx-tx)*(ns/scale);ty=my-(my-ty)*(ns/scale);scale=ns;draw();\n"
          + "  setHud(null,0);\n"
          + "}\n"
          + "cv.addEventListener('pointerdown',function(e){dragging=true;lx=e.clientX;ly=e.clientY;"
          + "cv.classList.add('drag');});\n"
          + "window.addEventListener('pointermove',function(e){"
          + "if(dragging){tx+=e.clientX-lx;ty+=e.clientY-ly;lx=e.clientX;ly=e.clientY;draw();}"
          + "var b=blockAt(e.clientX,e.clientY);setHud(b.x,b.z);"
          + "});\n"
          + "window.addEventListener('pointerup',function(){dragging=false;"
          + "cv.classList.remove('drag');});\n"
          + "cv.addEventListener('wheel',function(e){e.preventDefault();"
          + "var f=Math.exp(-e.deltaY*0.0015);zoomAt(e.clientX,e.clientY,scale*f);"
          + "},{passive:false});\n"
          // 触屏双指捏合
          + "var pinch0=0,pinchScale=1;\n"
          + "function tdist(t){var dx=t[0].clientX-t[1].clientX,dy=t[0].clientY-t[1].clientY;"
          + "return Math.sqrt(dx*dx+dy*dy);}\n"
          + "cv.addEventListener('touchstart',function(e){"
          + "if(e.touches.length==2){pinch0=tdist(e.touches);pinchScale=scale;dragging=false;}"
          + "},{passive:true});\n"
          + "cv.addEventListener('touchmove',function(e){"
          + "if(e.touches.length==2){e.preventDefault();"
          + "var d=tdist(e.touches);if(pinch0>0&&d>0){"
          + "var mx=(e.touches[0].clientX+e.touches[1].clientX)/2;"
          + "var my=(e.touches[0].clientY+e.touches[1].clientY)/2;"
          + "zoomAt(mx,my,pinchScale*d/pinch0);}}"
          + "},{passive:false});\n"
          + "cv.addEventListener('touchend',function(e){"
          + "if(e.touches.length<2){pinch0=0;}});\n"
          + "cv.addEventListener('dblclick',function(){fit();draw();setHud(null,0);});\n"
          + "window.addEventListener('resize',function(){resize();draw();});\n"
          + "var lg=document.getElementById('legend');\n"
          + "if(POINTS.length){var s='<div class=\"t\">标点 ('+POINTS.length+')</div>';"
          + "for(var i=0;i<POINTS.length;i++){s+='<div class=\"p\"><span class=\"dot\" style=\"background:'"
          + "+(POINTS[i].c)+'\"></span>'+POINTS[i].n+'</div>';}lg.innerHTML=s;}\n"
          + "else{lg.style.display='none';}\n"
          + "decodeRle();fit();draw();setHud(null,0);\n"
          + "})();\n</script>\n</body>\n</html>\n");
        return sb.toString();
    }

    /** 小端写 16 位（JS 端 DataView.getUint16(..., true) 小端读）。 */
    private static void writeShortLE(java.io.ByteArrayOutputStream bos, int v) {
        bos.write(v & 0xFF);
        bos.write((v >> 8) & 0xFF);
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "\\'").replace("\"", "&quot;");
    }
}
