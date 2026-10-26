// results.csv 是多轮跑出来的追加日志（156 行 / 49 个唯一组合）。这里按「每个组合取最后一次」
// 收敛成干净的表，顺序沿用第一次出现的顺序，并把旧的备份成 results-history.csv。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/verification/results.csv';
const raw = fs.readFileSync(p, 'utf8').replace(/^\uFEFF/, '');
const lines = raw.split(/\r?\n/).filter((l) => l.trim() !== '');
const header = lines[0];
const order = [];
const last = new Map();
for (const line of lines.slice(1)) {
  const cols = line.split(',');
  const key = cols[0];
  if (!last.has(key)) order.push(key);
  last.set(key, line);
}
fs.writeFileSync('D:/Development/watercraftlauncher/verification/results-history.csv', raw, 'utf8');
let out = header + '\n' + order.map((k) => last.get(k)).join('\n') + '\n';
fs.writeFileSync(p, out, 'utf8');

const rows = order.map((k) => last.get(k).split(','));
const ok = rows.filter((c) => c[4] === 'OK' && c[5] === 'OK').length;
console.log(`收敛：${lines.length - 1} 行 → ${order.length} 行；OK ${ok}/${order.length}`);
const byKind = {};
for (const c of rows) {
  const kind = c[2] === 'NONE' ? `OPTIFINE:${c[3]}` : c[2];
  byKind[kind] = (byKind[kind] || 0) + 1;
}
console.log(byKind);
