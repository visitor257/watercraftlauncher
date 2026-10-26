// 查 TextRenderer 在 1.16 / 1.16.1 / 1.16.2 / 1.16.5 里的方法描述符差异。
// 直接读 yarn mappings.tiny（已在 logs/build/yarn-cache 缓存），不生成 merged jar。
import fs from 'node:fs';
import path from 'node:path';

const cache = 'D:/Development/watercraftlauncher/logs/build/yarn-cache';
const versions = ['1.16', '1.16.1', '1.16.2', '1.16.5'];
const want = ['TextRenderer', 'DrawableHelper'];

for (const v of versions) {
  const f = path.join(cache, `${v}.tiny`);
  if (!fs.existsSync(f)) { console.log(`${v}: 无映射`); continue; }
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  let cur = null;
  const out = {};
  for (const line of lines) {
    if (line.startsWith('c\t')) {
      const p = line.split('\t');
      cur = p[3] || p[2];
    } else if (line.startsWith('\tm\t') && cur && want.indexOf(cur.split('/').pop()) >= 0) {
      const p = line.split('\t');
      const desc = p[2];
      const named = p[5] || p[4] || '?';
      if (!out[cur]) out[cur] = [];
      out[cur].push(named + ' ' + desc);
    }
  }
  console.log('\n=== ' + v);
  for (const cls of want) {
    const list = out['net/minecraft/client/' + (cls === 'TextRenderer' ? 'font/' : 'gui/') + cls] || [];
    const draws = list.filter(function (s) { return s.indexOf('draw ') === 0; });
    console.log('  ' + cls + ': ' + list.length + ' 个方法, draw 重载 ' + draws.length);
    draws.forEach(function (d) { console.log('    ' + d); });
  }
}
