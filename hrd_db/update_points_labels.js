// 标点 tab 标题与搜索 hint 改为「标点&结构」（11 语言）
const fs = require('fs');
const path = require('path');
const resDir = 'C:/Users/lishu/AppData/Roaming/CherryStudio/Data/Agents/zfb5j63kp/LeviLaunchroid/app/src/main/res';

const dirs = {
  en: 'values', zh: 'values-zh-rCN', es: 'values-es', fr: 'values-fr',
  hi: 'values-hi', idn: 'values-idn', ja: 'values-ja', pt: 'values-pt',
  ru: 'values-ru', tr: 'values-tr', vi: 'values-vi',
};
// [lb_points, point_search_hint]
const T = {
  en: ['Points & Structures', 'Search points & structures…'],
  zh: ['标点&结构', '搜索标点&结构…'],
  es: ['Puntos y estructuras', 'Buscar puntos y estructuras…'],
  fr: ['Points et structures', 'Rechercher points et structures…'],
  hi: ['बिंदु और संरचनाएं', 'बिंदु और संरचनाएं खोजें…'],
  idn: ['Titik & Struktur', 'Cari titik & struktur…'],
  ja: ['ポイントと構造物', 'ポイントと構造物を検索…'],
  pt: ['Pontos e estruturas', 'Buscar pontos e estruturas…'],
  ru: ['Точки и структуры', 'Поиск точек и структур…'],
  tr: ['Noktalar ve Yapılar', 'Nokta ve yapı ara…'],
  vi: ['Điểm & Công trình', 'Tìm điểm & công trình…'],
};

for (const [lang, dir] of Object.entries(dirs)) {
  const file = path.join(resDir, dir, 'strings.xml');
  let c = fs.readFileSync(file, 'utf8');
  const [pts, hint] = T[lang];
  c = c.replace(/<string name="lb_points">[^<]*<\/string>/,
      `<string name="lb_points">${pts}</string>`);
  c = c.replace(/<string name="point_search_hint">[^<]*<\/string>/,
      `<string name="point_search_hint">${hint}</string>`);
  fs.writeFileSync(file, c);
  console.log(lang, 'OK');
}
