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

    /** 拼图边长上限（超限按 2 的倍数降采样）。 */
    private static final int MAX_DIM = 4096;

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
        int[] full = MapImageComposer.compose(map);
        if (full == null) {
            throw new Exception("no rendered chunks");
        }
        int w = full[0], h = full[1];
        int[] px = new int[w * h];
        System.arraycopy(full, 2, px, 0, w * h);

        String data = encodeRle(px);
        String html = buildHtml(title, seed, version, w, h,
                map.minBlockX, map.minBlockZ, playerX, playerZ, spawnX, spawnZ,
                deathX, deathZ, points, data);

        java.io.File out = new java.io.File(outDir, fileName);
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            fos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out;
    }

    /** 调色板 + 游程编码：paletteCount(2B) + RGB×n(3B each) +
     *  runs(index 2B + count 2B)… → base64。 */
    private static String encodeRle(int[] px) throws java.io.IOException {
        java.util.LinkedHashMap<Integer, Integer> palette = new java.util.LinkedHashMap<>();
        int[] index = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            Integer id = palette.get(px[i]);
            if (id == null) {
                id = palette.size();
                palette.put(px[i], id);
            }
            index[i] = id;
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream(px.length);
        // 小端写（JS 端 DataView.getUint16(..., true) 小端读——大端写会
        // 字节序错位，HTML 黑屏根因）
        writeShortLE(bos, palette.size());
        for (int c : palette.keySet()) {
            bos.write((c >> 16) & 0xFF);
            bos.write((c >> 8) & 0xFF);
            bos.write(c & 0xFF);
        }
        for (int i = 0; i < index.length; ) {
            int v = index[i];
            int j = i + 1;
            while (j < index.length && index[j] == v && j - i < 65535) {
                j++;
            }
            writeShortLE(bos, v);
            writeShortLE(bos, j - i);
            i = j;
        }
        return android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
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
