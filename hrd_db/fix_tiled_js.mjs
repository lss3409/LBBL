// v0.4.93：buildTiledHtml 的 zoomAt/draw 调用点改快照预览
import fs from 'fs';
const f = 'app/src/main/java/org/levimc/launcher/core/content/worldmap/HtmlMapExporter.java';
let c = fs.readFileSync(f, 'utf8');

const tdIdx = c.indexOf('TDATA=[');
if (tdIdx < 0) throw new Error('TDATA not found');

// zoomAt：draw() → drawPreview()+debounceFull()（只改 tiled 版）
const oldZoom = 'scale=ns;draw();';
const zPos = c.indexOf(oldZoom, tdIdx);
if (zPos < 0) throw new Error('zoomAt not found');
c = c.substring(0, zPos) + 'scale=ns;\n'
  + '          + "  drawPreview();debounceFull();'
  + c.substring(zPos + oldZoom.length + 1); // 跳过 draw(); 后多余的 ";\n"

// 修正：上面替换把 "draw();\n" 里的 ";" 吞掉，需要检查原串结构——
// 原串：scale=ns;draw();\n" + "  setHud...  => 新：scale=ns;\n" + "  drawPreview...
// 直接重新读文件用更精确替换
c = fs.readFileSync(f, 'utf8'); // 重新读（上面替换可能坏了）
// 重新做：只在 tiled 段替换完整 zoomAt 函数体
const zoomOld = `scale=ns;draw();`;
const zoomNew = `scale=ns;\n          + "  drawPreview();debounceFull();`;
const zi = c.indexOf(zoomOld, tdIdx);
if (zi < 0) throw new Error('zoom 2nd not found');
c = c.substring(0, zi) + zoomNew + c.substring(zi + zoomOld.length);

// pointermove 拖拽 draw() → fullDraw()
const pdOld = 'if(dragging){tx+=e.clientX-lx;ty+=e.clientY-ly;lx=e.clientX;ly=e.clientY;draw();}';
const pdNew = 'if(dragging){tx+=e.clientX-lx;ty+=e.clientY-ly;lx=e.clientX;ly=e.clientY;fullDraw();}';
const pi = c.indexOf(pdOld, tdIdx);
if (pi >= 0) c = c.substring(0, pi) + pdNew + c.substring(pi + pdOld.length);

// dblclick / resize / 初始化（tiled 段）
const t = c.substring(tdIdx);
const t2 = t
  .replace('function(){fit();draw();setHud(null,0);}', 'function(){fit();fullDraw();setHud(null,0);}')
  .replace('function(){resize();draw();}', 'function(){resize();fullDraw();}')
  .replace('resize();fit();draw();setHud(null,0);', 'resize();fit();fullDraw();setHud(null,0);');
c = c.substring(0, tdIdx) + t2;

fs.writeFileSync(f, c);
console.log('done');
