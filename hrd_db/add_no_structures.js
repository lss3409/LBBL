// no_structures 词条（11 语言）
const fs = require('fs');
const path = require('path');
const resDir = 'C:/Users/lishu/AppData/Roaming/CherryStudio/Data/Agents/zfb5j63kp/LeviLaunchroid/app/src/main/res';
const dirs = {
  en: 'values', zh: 'values-zh-rCN', es: 'values-es', fr: 'values-fr',
  hi: 'values-hi', idn: 'values-idn', ja: 'values-ja', pt: 'values-pt',
  ru: 'values-ru', tr: 'values-tr', vi: 'values-vi',
};
const T = {
  en: 'No structures', zh: '无结构', es: 'Sin estructuras', fr: 'Aucune structure',
  hi: 'कोई संरचना नहीं', idn: 'Tidak ada struktur', ja: '構造物なし',
  pt: 'Sem estruturas', ru: 'Нет структур', tr: 'Yapı yok',
  vi: 'Không có công trình',
};
for (const [lang, dir] of Object.entries(dirs)) {
  const file = path.join(resDir, dir, 'strings.xml');
  let c = fs.readFileSync(file, 'utf8');
  if (!c.includes('name="no_structures"')) {
    c = c.replace('</resources>',
      `    <string name="no_structures">${T[lang]}</string>\n</resources>`);
    fs.writeFileSync(file, c);
  }
  console.log(lang, 'OK');
}
