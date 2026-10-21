// v0.4.87：回滚全图绘制到 v0.4.53 精神（恒全图单 drawBitmap）+ 删 mip 链
import fs from 'fs';

const f = 'app/src/main/java/org/levimc/launcher/ui/views/WorldMapView.java';
let w = fs.readFileSync(f, 'utf8');

// 1) onDraw 条件：blockScale=1 恒全图；blockScale>1 仅真缩小
const condStart = '        if (fullMapBitmap != null && !fullMapBitmap.isRecycled()';
const condEnd = '        } else if (map.chunkColors != null) {';
const i1 = w.indexOf(condStart);
const i2 = w.indexOf(condEnd, i1);
if (i1 < 0 || i2 < 0) throw new Error('cond block not found');
const newCond = `        if (fullMapBitmap != null && !fullMapBitmap.isRecycled()
                && (fullMapBlockScale <= 1
                        || pixelsPerBlock <= 1f / Math.max(1f, fullMapBlockScale))) {
            // v0.4.87：回滚到"恒全图单 drawBitmap"（用户拍板：缩放永远
            // 相当于显示一张图片的 GPU 变换=HTML/图片查看器同款，任何
            // 缩放都不卡）。blockScale=1（小世界/主世界大图）恒走全图；
            // 下界降采样全图仅真缩小（scalePx<=1）时用，放大回 tile
            drawFullMapLayer(canvas);
`;
w = w.substring(0, i1) + newCond + w.substring(i2);

// 2) drawFullMapLayer 简化：删 mip 选级，缩小=双线性
const mipStart = w.indexOf('            android.graphics.Bitmap mip = null;');
const mipEnd = w.indexOf('        }\n    }', mipStart);
if (mipStart < 0 || mipEnd < 0) throw new Error('mip block not found');
const newDraw = `            if (fullMapLinearPaint == null) {
                fullMapLinearPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
            }
            canvas.drawBitmap(fullMapBitmap, null, fullMapDst, fullMapLinearPaint);
`;
w = w.substring(0, mipStart) + newDraw + w.substring(mipEnd);

// 3) 删 installFullMap 里的 mipGen 与注释（保留基本赋值）
w = w.replace(`        // v0.4.84：mip 按需生成（不再挂载时全量提交三级——单线程队列
        // 堆积导致最新全图的 mip 迟到数十秒="刚打开糊过一会不糊"；
        // 打开 fit 视图只缺 mip8，按需生成几十 ms 即就绪）
        if (bmp != null && !bmp.isRecycled()) {
            mipGen++;
        }
        invalidateFullRender();`, `        invalidateFullRender();`);

// 4) 删 ensureMip 方法 + mipLock/mipPending 字段
const emStart = w.indexOf('    /** 按需生成某级 mip（2/4/8）；生成中或已就绪则跳过。 */');
const emEnd = w.indexOf('    private void recycleMips() {', emStart);
if (emStart > 0 && emEnd > emStart) {
  w = w.substring(0, emStart) + w.substring(emEnd);
}

// 5) mip 字段声明块删除（fullMapMip2/4/8、mipGen、mipExecutor）
const fsStart = w.indexOf('    // ---- v0.4.75：全图 mipmap 金字塔');
const fsEnd = w.indexOf('    /** 设置全图纹理', fsStart);
if (fsStart > 0 && fsEnd > fsStart) {
  w = w.substring(0, fsStart) + w.substring(fsEnd);
}

// 6) recycleMips 简化为空操作（installFullMap/clearFullMapImage 仍调用）
w = w.replace(`    private void recycleMips() {
        if (fullMapMip2 != null && !fullMapMip2.isRecycled()) fullMapMip2.recycle();
        if (fullMapMip4 != null && !fullMapMip4.isRecycled()) fullMapMip4.recycle();
        if (fullMapMip8 != null && !fullMapMip8.isRecycled()) fullMapMip8.recycle();
        fullMapMip2 = null;
        fullMapMip4 = null;
        fullMapMip8 = null;
    }`, `    private void recycleMips() {
        // v0.4.87：mip 链已删除（回滚 v0.4.53 恒全图单 draw）
    }`);

fs.writeFileSync(f, w);
const left = (w.match(/fullMapMip|mipGen|ensureMip|mipPending|mipLock/g) || []).length;
console.log('done, leftover refs:', left);
