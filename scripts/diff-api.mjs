// 通用：对着 yarn mappings.tiny 打印某方法在各版本的描述符（不经 merged jar，秒出）。
// 用法： node scripts/diff-api.mjs 1.19 1.19.2 1.19.4 -- TextRenderer:draw Screen:renderBackground
import fs from 'node:fs';

const args = process.argv.slice(2);
const sep = args.indexOf('--');
const versions = sep > 0 ? args.slice(0, sep) : args;
const specs = sep > 0 ? args.slice(sep + 1) : [];
const cache = 'D:/Development/watercraftlauncher/logs/build/yarn-cache';

function load(v) {
  const f = cache + '/' + v + '.tiny';
  const lines = fs.readFileSync(f, 'utf8').split('\n');
  let cur = null;
  let curShort = null;
  const out = Object.create(null);
  for (const line of lines) {
    if (line.startsWith('c\t')) {
      const p = line.split('\t');
      cur = p[2];
      curShort = cur.split('/').pop();
    } else if (line.startsWith('\tm\t') && cur) {
      const p = line.split('\t');
      if (!out[curShort]) out[curShort] = Object.create(null);
      const named = p[4] || '?';
      if (!out[curShort][named]) out[curShort][named] = [];
      out[curShort][named].push(p[2]);
    }
  }
  return out;
}

const data = {};
for (const v of versions) data[v] = load(v);

for (const spec of specs) {
  const [cls, m] = spec.split(':');
  console.log('\n### ' + cls + '#' + m);
  for (const v of versions) {
    const d = ((data[v][cls] || {})[m] || []).sort();
    console.log('  ' + v.padEnd(8) + (d.length ? d.join('\n           ') : '—— 没有'));
  }
}
