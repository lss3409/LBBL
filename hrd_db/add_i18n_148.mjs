import fs from 'fs';
const base = 'C:/Users/lishu/AppData/Roaming/CherryStudio/Data/Agents/zfb5j63kp/LeviLaunchroid/app/src/main/res/';
const T = {
  en: { rebake_map: "Rebake Map", rebake_map_done: "Bake cache cleared" },
  zh: { rebake_map: "重新烘焙地图", rebake_map_done: "烘焙缓存已清除" },
  es: { rebake_map: "Volver a renderizar el mapa", rebake_map_done: "Caché de renderizado borrada" },
  fr: { rebake_map: "Re-rendu de la carte", rebake_map_done: "Cache de rendu effacée" },
  hi: { rebake_map: "मानचित्र फिर से बेक करें", rebake_map_done: "बेक कैश साफ़ हो गया" },
  idn: { rebake_map: "Panggang Ulang Peta", rebake_map_done: "Cache panggang dibersihkan" },
  ja: { rebake_map: "マップを再ベイク", rebake_map_done: "ベイクキャッシュを消去しました" },
  pt: { rebake_map: "Re-renderizar mapa", rebake_map_done: "Cache de renderização limpo" },
  ru: { rebake_map: "Перезапечь карту", rebake_map_done: "Кэш запекания очищен" },
  tr: { rebake_map: "Haritayı Yeniden Pişir", rebake_map_done: "Pişirme önbelleği temizlendi" },
  vi: { rebake_map: "Nướng lại bản đồ", rebake_map_done: "Đã xóa bộ nhớ đệm nướng" }
};
const dirs = { en:'values', zh:'values-zh-rCN', es:'values-es', fr:'values-fr', hi:'values-hi', idn:'values-idn', ja:'values-ja', pt:'values-pt', ru:'values-ru', tr:'values-tr', vi:'values-vi' };
for (const [lang, dir] of Object.entries(dirs)) {
    const p = base + dir + '/strings.xml';
    let txt = fs.readFileSync(p, 'utf8');
    const existing = new Set([...txt.matchAll(/<string name="([^"]+)"/g)].map(m => m[1]));
    const rows = [];
    for (const [k, v] of Object.entries(T[lang])) {
        if (existing.has(k)) continue;
        rows.push('    <string name="' + k + '">' + v + '</string>');
    }
    if (!rows.length) { console.log(lang, 'skip'); continue; }
    txt = txt.replace('</resources>', rows.join('\n') + '\n</resources>');
    fs.writeFileSync(p, txt);
    console.log(lang, 'added', rows.length);
}
