// 1.16 系列内部要用的那几个方法，逐个版本对一遍（决定 1.16 jar 能不能一份盖全系）
import fs from 'node:fs';

const cache = 'D:/Development/watercraftlauncher/logs/build/yarn-cache';
const versions = ['1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5'];
const want = {
  'net/minecraft/client/render/item/ItemRenderer': ['renderInGui', 'renderGuiItemModel'],
  'net/minecraft/client/gui/DrawableHelper': ['fill', 'drawTexture'],
  'net/minecraft/client/font/TextRenderer': ['draw', 'getWidth'],
  'net/minecraft/client/gui/screen/Screen': ['render', 'renderBackground', 'init', 'openScreen'],
};

function load(v) {
  const f = cache + '/' + v + '.tiny';
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  let cur = null;
  const out = Object.create(null);
  for (const line of lines) {
    if (line.startsWith('c\t')) {
      const p = line.split('\t');
      cur = p[2];
    } else if (line.startsWith('\tm\t') && cur) {
      const p = line.split('\t');
      const desc = p[2];
      const named = p[4] || '?';
      const key = cur;
      if (!out[key]) out[key] = Object.create(null);
      if (!out[key][named]) out[key][named] = [];
      out[key][named].push(desc);
    }
  }
  return out;
}

const data = {};
for (const v of versions) data[v] = load(v);

for (const cls of Object.keys(want)) {
  console.log('\n### ' + cls);
  for (const m of want[cls]) {
    console.log('  ' + m);
    for (const v of versions) {
      const d = (data[v][cls] || {})[m] || [];
      console.log('    ' + v.padEnd(7) + (d.length ? d.join(' | ') : ' —— 没有'));
    }
  }
}
