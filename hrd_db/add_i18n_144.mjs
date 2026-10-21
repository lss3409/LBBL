import fs from 'fs';
const base = 'C:/Users/lishu/AppData/Roaming/CherryStudio/Data/Agents/zfb5j63kp/LeviLaunchroid/app/src/main/res/';

const T = {
  en: { hud_fps_zoom:"FPS %1$.0f  ·  Zoom %2$.3f px", hud_blocks_per_pixel:"%1$.2f blocks/px", hud_gesture_scale:"Pinch zoom", hud_gesture_pan:"Panning", hud_gesture_idle:"Idle", hud_center:" · Center ", hud_pointer_biome:"Pointer biome %1$s", hud_pin_always:"Pointer always on", hud_pin_center:"Pointer fixed to center", hud_marker_count:"Markers E%1$d O%2$d S%3$d", bake_preparing:"Preparing bake…", bake_progress:"Baking %1$d/%2$d chunks", bake_finalizing:" · Finalizing…" },
  zh: { hud_fps_zoom:"FPS %1$.0f  ·  缩放 %2$.3f px", hud_blocks_per_pixel:"%1$.2f 方块/像素", hud_gesture_scale:"捏合缩放", hud_gesture_pan:"滑动", hud_gesture_idle:"静止", hud_center:" · 中心 ", hud_pointer_biome:"指针群系 %1$s", hud_pin_always:"指针始终显示", hud_pin_center:"指针固定中心", hud_marker_count:"标点 实体%1$d 矿石%2$d 结构%3$d", bake_preparing:"烘焙准备中…", bake_progress:"烘焙中 %1$d/%2$d 区块", bake_finalizing:" · 落盘中…" },
  es: { hud_fps_zoom:"FPS %1$.0f  ·  Zoom %2$.3f px", hud_blocks_per_pixel:"%1$.2f bloques/px", hud_gesture_scale:"Pellizco", hud_gesture_pan:"Deslizar", hud_gesture_idle:"Quieto", hud_center:" · Centro ", hud_pointer_biome:"Bioma del puntero %1$s", hud_pin_always:"Puntero siempre visible", hud_pin_center:"Puntero fijo al centro", hud_marker_count:"Marcadores E%1$d O%2$d S%3$d", bake_preparing:"Preparando horneado…", bake_progress:"Horneando %1$d/%2$d chunks", bake_finalizing:" · Finalizando…" },
  fr: { hud_fps_zoom:"FPS %1$.0f  ·  Zoom %2$.3f px", hud_blocks_per_pixel:"%1$.2f blocs/px", hud_gesture_scale:"Pincer", hud_gesture_pan:"Glisser", hud_gesture_idle:"Repos", hud_center:" · Centre ", hud_pointer_biome:"Biome du pointeur %1$s", hud_pin_always:"Pointeur toujours visible", hud_pin_center:"Pointeur fixé au centre", hud_marker_count:"Marqueurs E%1$d O%2$d S%3$d", bake_preparing:"Préparation de la cuisson…", bake_progress:"Cuisson %1$d/%2$d chunks", bake_finalizing:" · Finalisation…" },
  hi: { hud_fps_zoom:"FPS %1$.0f  ·  ज़ूम %2$.3f px", hud_blocks_per_pixel:"%1$.2f ब्लॉक/px", hud_gesture_scale:"पिंच ज़ूम", hud_gesture_pan:"पैन", hud_gesture_idle:"स्थिर", hud_center:" · केंद्र ", hud_pointer_biome:"पॉइंटर बायोम %1$s", hud_pin_always:"पॉइंटर हमेशा दिखाएँ", hud_pin_center:"पॉइंटर केंद्र पर स्थिर", hud_marker_count:"मार्कर E%1$d O%2$d S%3$d", bake_preparing:"बेक तैयार हो रहा है…", bake_progress:"बेक हो रहा है %1$d/%2$d चंक", bake_finalizing:" · अंतिम रूप…" },
  idn: { hud_fps_zoom:"FPS %1$.0f  ·  Zoom %2$.3f px", hud_blocks_per_pixel:"%1$.2f blok/px", hud_gesture_scale:"Cubit zoom", hud_gesture_pan:"Geser", hud_gesture_idle:"Diam", hud_center:" · Pusat ", hud_pointer_biome:"Bioma penunjuk %1$s", hud_pin_always:"Penunjuk selalu tampil", hud_pin_center:"Penunjuk tetap di tengah", hud_marker_count:"Penanda E%1$d O%2$d S%3$d", bake_preparing:"Menyiapkan panggang…", bake_progress:"Memanggang %1$d/%2$d chunk", bake_finalizing:" · Menyelesaikan…" },
  ja: { hud_fps_zoom:"FPS %1$.0f  ·  ズーム %2$.3f px", hud_blocks_per_pixel:"%1$.2f ブロック/px", hud_gesture_scale:"ピンチズーム", hud_gesture_pan:"スライド", hud_gesture_idle:"静止", hud_center:" · 中心 ", hud_pointer_biome:"ポインターのバイオーム %1$s", hud_pin_always:"ポインターを常に表示", hud_pin_center:"ポインターを中心に固定", hud_marker_count:"マーカー 実体%1$d 鉱石%2$d 構造%3$d", bake_preparing:"ベイク準備中…", bake_progress:"ベイク中 %1$d/%2$d チャンク", bake_finalizing:" · 書き込み中…" },
  pt: { hud_fps_zoom:"FPS %1$.0f  ·  Zoom %2$.3f px", hud_blocks_per_pixel:"%1$.2f blocos/px", hud_gesture_scale:"Pinça", hud_gesture_pan:"Arrastar", hud_gesture_idle:"Parado", hud_center:" · Centro ", hud_pointer_biome:"Bioma do ponteiro %1$s", hud_pin_always:"Ponteiro sempre visível", hud_pin_center:"Ponteiro fixo ao centro", hud_marker_count:"Marcadores E%1$d O%2$d S%3$d", bake_preparing:"Preparando cozimento…", bake_progress:"Cozinhando %1$d/%2$d chunks", bake_finalizing:" · Finalizando…" },
  ru: { hud_fps_zoom:"FPS %1$.0f  ·  Масштаб %2$.3f px", hud_blocks_per_pixel:"%1$.2f блоков/px", hud_gesture_scale:"Щипок", hud_gesture_pan:"Сдвиг", hud_gesture_idle:"Покой", hud_center:" · Центр ", hud_pointer_biome:"Биом указателя %1$s", hud_pin_always:"Указатель всегда виден", hud_pin_center:"Указатель закреплён в центре", hud_marker_count:"Маркеры Э%1$d Р%2$d С%3$d", bake_preparing:"Подготовка запекания…", bake_progress:"Запекание %1$d/%2$d чанков", bake_finalizing:" · Завершение…" },
  tr: { hud_fps_zoom:"FPS %1$.0f  ·  Yakınlaştırma %2$.3f px", hud_blocks_per_pixel:"%1$.2f blok/px", hud_gesture_scale:"Kıstırma", hud_gesture_pan:"Kaydırma", hud_gesture_idle:"Durağan", hud_center:" · Merkez ", hud_pointer_biome:"İşaretçi biyomu %1$s", hud_pin_always:"İşaretçi her zaman görünür", hud_pin_center:"İşaretçi merkeze sabit", hud_marker_count:"İşaretler V%1$d C%2$d Y%3$d", bake_preparing:"Pişirme hazırlanıyor…", bake_progress:"Pişiriliyor %1$d/%2$d parça", bake_finalizing:" · Tamamlanıyor…" },
  vi: { hud_fps_zoom:"FPS %1$.0f  ·  Thu phóng %2$.3f px", hud_blocks_per_pixel:"%1$.2f khối/px", hud_gesture_scale:"Chụm thu phóng", hud_gesture_pan:"Trượt", hud_gesture_idle:"Đứng yên", hud_center:" · Tâm ", hud_pointer_biome:"Quần xã tại con trỏ %1$s", hud_pin_always:"Con trỏ luôn hiển thị", hud_pin_center:"Con trỏ cố định tâm", hud_marker_count:"Đánh dấu E%1$d O%2$d S%3$d", bake_preparing:"Chuẩn bị nướng…", bake_progress:"Đang nướng %1$d/%2$d chunk", bake_finalizing:" · Hoàn tất…" }
};

const dirs = { en:'values', zh:'values-zh-rCN', es:'values-es', fr:'values-fr', hi:'values-hi', idn:'values-idn', ja:'values-ja', pt:'values-pt', ru:'values-ru', tr:'values-tr', vi:'values-vi' };
for (const [lang, dir] of Object.entries(dirs)) {
    const p = base + dir + '/strings.xml';
    let txt = fs.readFileSync(p, 'utf8');
    const existing = new Set([...txt.matchAll(/<string name="([^"]+)"/g)].map(m => m[1]));
    const rows = [];
    for (const [k, v] of Object.entries(T[lang])) {
        if (existing.has(k)) continue;
        const esc = v.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        rows.push('    <string name="' + k + '">' + esc + '</string>');
    }
    if (!rows.length) { console.log(lang, 'nothing to add'); continue; }
    txt = txt.replace('</resources>', rows.join('\n') + '\n</resources>');
    fs.writeFileSync(p, txt);
    console.log(lang, 'added', rows.length);
}
