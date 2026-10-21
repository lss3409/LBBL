import fs from 'fs';
const dirs = ['values', 'values-zh-rCN', 'values-es', 'values-fr', 'values-hi',
    'values-idn', 'values-ja', 'values-pt', 'values-ru', 'values-tr', 'values-vi'];
for (const d of dirs) {
    const p = 'app/src/main/res/' + d + '/strings.xml';
    const lines = fs.readFileSync(p, 'utf8').split('\n');
    let changed = 0;
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (!line.includes('<string name=')) continue;
        // 只替换「非反斜杠前缀」的撇号（已有 \' 的不动）
        const fixed = line.replace(/([^\\])'/g, "$1\\'");
        if (fixed !== line) { lines[i] = fixed; changed++; }
    }
    if (changed) { fs.writeFileSync(p, lines.join('\n')); console.log(d, 'fixed', changed, 'lines'); }
}
