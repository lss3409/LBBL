// 给 11 个语言的 strings.xml 插入结构标签词条
const fs = require('fs');
const path = require('path');

const resDir = 'C:/Users/lishu/AppData/Roaming/CherryStudio/Data/Agents/zfb5j63kp/LeviLaunchroid/app/src/main/res';

// 23 个词条：key -> {en: ..., zh: ..., es: ..., fr: ..., hi: ..., idn: ..., ja: ..., pt: ..., ru: ..., tr: ..., vi: ...}
const KEYS = [
  'village', 'spawner', 'trial_spawner', 'end_portal', 'fortress',
  'swamp_hut', 'ocean_monument', 'outpost', 'desert_temple', 'end_city',
  'desert_pyramid', 'jungle_temple', 'igloo', 'ocean_ruin', 'shipwreck',
  'woodland_mansion', 'pillager_outpost', 'ruined_portal', 'ancient_city',
  'buried_treasure', 'mineshaft', 'trail_ruins', 'trial_chambers',
];

const T = {
  village: ['Village', '村庄', 'Aldea', 'Village', 'गांव', 'Desa', '村', 'Vila', 'Деревня', 'Köy', 'Làng'],
  spawner: ['Monster Spawner', '刷怪笼', 'Generador de criaturas', 'Générateur de monstres', 'मॉन्स्टर स्पॉनर', 'Monster Spawner', 'モンスタースポナー', 'Gerador de monstros', 'Спаунер монстров', 'Canavar Spawner\\\'ı', 'Đài sinh vật'],
  trial_spawner: ['Trial Spawner', '试炼刷怪笼', 'Generador de pruebas', 'Générateur d\\\'épreuves', 'ट्रायल स्पॉनर', 'Trial Spawner', '試練のスポナー', 'Gerador de desafio', 'Спаунер испытаний', 'Deneme Spawner\\\'ı', 'Đài sinh vật thử thách'],
  end_portal: ['End Portal', '末地传送门', 'Portal del End', 'Portail de l\\\'End', 'एंड पोर्टल', 'Portal End', 'エンドポータル', 'Portal do End', 'Портал в Энд', 'End Portalı', 'Cổng End'],
  fortress: ['Nether Fortress', '下界要塞', 'Fortaleza del Nether', 'Forteresse du Nether', 'नर्क का किला', 'Benteng Nether', 'ネザー要塞', 'Fortaleza do Nether', 'Крепость Нижнего мира', 'Nether Kalesi', 'Pháo đài Nether'],
  swamp_hut: ['Swamp Hut', '女巫小屋', 'Cabaña de bruja', 'Hutte de sorcière', 'चुड़ैल की झोपड़ी', 'Gubuk Penyihir', '魔女の小屋', 'Cabana da bruxa', 'Хижина ведьмы', 'Cadı Kulübesi', 'Túp lều phù thủy'],
  ocean_monument: ['Ocean Monument', '海底神殿', 'Monumento oceánico', 'Monument sous-marin', 'महासागर स्मारक', 'Monumen Laut', '海底神殿', 'Monumento oceânico', 'Океанский монумент', 'Okyanus Anıtı', 'Đài tưởng niệm đại dương'],
  outpost: ['Pillager Outpost', '掠夺者前哨站', 'Puesto de saqueadores', 'Avant-poste de pilleurs', 'लुटेरों की चौकी', 'Pos Penjarah', 'ピリジャーの前哨基地', 'Posto avançado de saqueadores', 'Аванпост разбойников', 'Yağmacı Karakolu', 'Tiền đồn cướp bóc'],
  desert_temple: ['Desert Temple', '沙漠神殿', 'Templo del desierto', 'Temple du désert', 'रेगिस्तानी मंदिर', 'Kuil Gurun', '砂漠の寺院', 'Templo do deserto', 'Храм пустыни', 'Çöl Tapınağı', 'Đền sa mạc'],
  end_city: ['End City', '末地城', 'Ciudad del End', 'Cité de l\\\'End', 'एंड सिटी', 'Kota End', 'エンドシティ', 'Cidade do End', 'Город Энда', 'End Şehri', 'Thành phố End'],
  desert_pyramid: ['Desert Pyramid', '沙漠神殿', 'Pirámide del desierto', 'Pyramide du désert', 'रेगिस्तानी पिरामिड', 'Piramida Gurun', '砂漠のピラミッド', 'Pirâmide do deserto', 'Пирамида пустыни', 'Çöl Piramidi', 'Kim tự tháp sa mạc'],
  jungle_temple: ['Jungle Temple', '丛林神庙', 'Templo de la selva', 'Temple de la jungle', 'जंगल मंदिर', 'Kuil Hutan', 'ジャングルの寺院', 'Templo da selva', 'Храм джунглей', 'Orman Tapınağı', 'Đền rừng già'],
  igloo: ['Igloo', '雪屋', 'Iglú', 'Iglou', 'इग्लू', 'Igloo', 'イグルー', 'Iglu', 'Иглу', 'İglo', 'Lều tuyết'],
  ocean_ruin: ['Ocean Ruin', '海底遗迹', 'Ruinas oceánicas', 'Ruines sous-marines', 'महासागर खंडहर', 'Reruntuhan Laut', '海底遺跡', 'Ruínas oceânicas', 'Океанские руины', 'Okyanus Kalıntıları', 'Tàn tích đại dương'],
  shipwreck: ['Shipwreck', '沉船', 'Naufragio', 'Épave', 'जहाज़ का मलबा', 'Kapal Karam', '難破船', 'Naufrágio', 'Затонувший корабль', 'Batık Gemi', 'Tàu đắm'],
  woodland_mansion: ['Woodland Mansion', '林地府邸', 'Mansión del bosque', 'Manoir des bois', 'वन हवेली', 'Rumah Hutan', '森の洋館', 'Mansão da floresta', 'Лесной особняк', 'Ormanlık Köşk', 'Biệt thự rừng'],
  pillager_outpost: ['Pillager Outpost', '掠夺者前哨站', 'Puesto de saqueadores', 'Avant-poste de pilleurs', 'लुटेरों की चौकी', 'Pos Penjarah', 'ピリジャーの前哨基地', 'Posto avançado de saqueadores', 'Аванпост разбойников', 'Yağmacı Karakolu', 'Tiền đồn cướp bóc'],
  ruined_portal: ['Ruined Portal', '废弃传送门', 'Portal en ruinas', 'Portail en ruine', 'खंडहर पोर्टल', 'Portal Rusak', '荒廃したポータル', 'Portal em ruínas', 'Разрушенный портал', 'Harabe Portal', 'Cổng đổ nát'],
  ancient_city: ['Ancient City', '远古城市', 'Ciudad antigua', 'Ancienne cité', 'प्राचीन शहर', 'Kota Kuno', '古代都市', 'Cidade antiga', 'Древний город', 'Antik Şehir', 'Thành phố cổ đại'],
  buried_treasure: ['Buried Treasure', '埋藏宝藏', 'Tesoro enterrado', 'Trésor enfoui', 'दबा हुआ खजाना', 'Harta Karun Terkubur', '埋もれた宝', 'Tesouro enterrado', 'Закопанный клад', 'Gömülü Hazine', 'Kho báu chôn giấu'],
  mineshaft: ['Mineshaft', '废弃矿井', 'Mina abandonada', 'Mine abandonnée', 'परित्यक्त खदान', 'Tambang Terbengkalai', '廃坑', 'Mina abandonada', 'Заброшенная шахта', 'Terk Edilmiş Maden', 'Mỏ bỏ hoang'],
  trail_ruins: ['Trail Ruins', '遗迹废墟', 'Ruinas de senderos', 'Ruines de sentiers', 'ट्रेल खंडहर', 'Reruntuhan Jejak', '道の遺跡', 'Ruínas de trilha', 'Руины троп', 'Patika Kalıntıları', 'Tàn tích đường mòn'],
  trial_chambers: ['Trial Chambers', '试炼密室', 'Cámaras del desafío', 'Chambres d\\\'épreuves', 'ट्रायल चैंबर', 'Ruang Percobaan', '試練の間', 'Câmaras de desafio', 'Камеры испытаний', 'Deneme Odaları', 'Phòng thử thách'],
};

const dirs = {
  en: 'values', zh: 'values-zh-rCN', es: 'values-es', fr: 'values-fr',
  hi: 'values-hi', idn: 'values-idn', ja: 'values-ja', pt: 'values-pt',
  ru: 'values-ru', tr: 'values-tr', vi: 'values-vi',
};
const LANG_IDX = { en: 0, zh: 1, es: 2, fr: 3, hi: 4, idn: 5, ja: 6, pt: 7, ru: 8, tr: 9, vi: 10 };

for (const [lang, dir] of Object.entries(dirs)) {
  const file = path.join(resDir, dir, 'strings.xml');
  let content = fs.readFileSync(file, 'utf8');
  if (!content.includes('structure_label_village')) {
    let block = '\n    <!-- v0.4.61：地图结构标记标签 -->\n';
    for (const k of KEYS) {
      const val = T[k][LANG_IDX[lang]];
      block += `    <string name="structure_label_${k}">${val}</string>\n`;
    }
    content = content.replace('</resources>', block + '</resources>');
    fs.writeFileSync(file, content);
    console.log(lang, 'OK');
  } else {
    console.log(lang, 'SKIP(已有)');
  }
}
