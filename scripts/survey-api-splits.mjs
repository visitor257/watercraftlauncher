// 「一个 jar 盖一个 1.xx 系列」之前必须知道的东西：**系列内部**哪些钩子签名变了。
//
// 做法：拉每个 MC 版本的 yarn 映射（mappings.tiny，几 MB，很快），直接读描述符。
// 比 javap merged jar 便宜得多（那要先让 loom 生成 23 个版本的 merged jar，每个几百 MB）。
//
// 输出：每个关心的类/方法在各版本里的描述符 → 一眼看出分裂点在哪。
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const root = 'D:/Development/watercraftlauncher';
const cache = path.join(root, 'logs/build/yarn-cache');
fs.mkdirSync(cache, { recursive: true });

const versions = [
  '1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5',
  '1.17', '1.17.1',
  '1.18', '1.18.1', '1.18.2',
  '1.19', '1.19.1', '1.19.2', '1.19.3', '1.19.4',
  '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
  '1.21', '1.21.1', '1.21.11',
];

// 关心的类 → 关心的方法名（yarn 名）
const WATCH = {
  'net/minecraft/client/gui/screen/Screen': [
    'render', 'renderBackground', 'mouseScrolled', 'keyPressed', 'shouldPause',
    'close', 'onClose', 'addDrawableChild', 'clearChildren', 'init',
  ],
  'net/minecraft/client/gui/DrawContext': ['drawText', 'drawTextWithShadow', 'fill', 'drawItem'],
  'net/minecraft/item/Item$Settings': ['maxCount', 'registryKey', 'maxDamage'],
  'net/minecraft/client/option/KeyBinding': ['<init>'],
  'net/minecraft/client/option/GameOptions': ['useKey'],
};

async function latestYarn(mc) {
  const r = await fetch(`https://meta.fabricmc.net/v2/versions/yarn/${mc}`);
  if (!r.ok) throw new Error(`meta ${mc} HTTP ${r.status}`);
  const list = await r.json();
  if (!list.length) throw new Error(`没有 ${mc} 的 yarn`);
  return list[0].version; // 已按 build 号倒序
}

async function getTiny(mc) {
  const f = path.join(cache, `${mc}.tiny`);
  if (fs.existsSync(f) && fs.statSync(f).size > 1000) return fs.readFileSync(f, 'utf8');
  const yv = await latestYarn(mc);
  const url = `https://maven.fabricmc.net/net/fabricmc/yarn/${yv}/yarn-${yv}-v2.jar`;
  const r = await fetch(url);
  if (!r.ok) throw new Error(`yarn ${yv} HTTP ${r.status}`);
  const jar = Buffer.from(await r.arrayBuffer());
  // 从 zip 里取出 mappings/mappings.tiny（deflate raw）
  const text = unzipOne(jar, 'mappings/mappings.tiny');
  fs.writeFileSync(f, text);
  return text;
}

function unzipOne(buf, wantName) {
  // 扫本地文件头 + 中央目录：只处理 stored/deflate，够用了
  let i = 0;
  while (i < buf.length - 30) {
    if (buf.readUInt32LE(i) !== 0x04034b50) { i++; continue; }
    const method = buf.readUInt16LE(i + 8);
    let compSize = buf.readUInt32LE(i + 18);
    const nameLen = buf.readUInt16LE(i + 26);
    const extraLen = buf.readUInt16LE(i + 28);
    const name = buf.slice(i + 30, i + 30 + nameLen).toString('utf8');
    const dataStart = i + 30 + nameLen + extraLen;
    if (name === wantName) {
      if (compSize === 0) {
        // 中央目录里才有真实大小，这里退回「读到下一个头」
        let end = dataStart;
        while (end < buf.length - 4 && buf.readUInt32LE(end) !== 0x04034b50 && buf.readUInt32LE(end) !== 0x02014b50) end++;
        compSize = end - dataStart;
      }
      const data = buf.slice(dataStart, dataStart + compSize);
      return method === 0 ? data.toString('utf8') : zlib.inflateRawSync(data).toString('utf8');
    }
    i = dataStart + (compSize || 1);
  }
  throw new Error('zip 里没找到 ' + wantName);
}

function parseTiny(text) {
  const out = {}; // class(yarn) -> { method -> [desc] }
  let cur = null;
  for (const line of text.split('\n')) {
    if (line.startsWith('c\t')) {
      const p = line.split('\t');
      const named = p[3] || p[2];
      cur = named;
      out[cur] = Object.create(null);
    } else if (line.startsWith('\tm\t') && cur) {
      const p = line.split('\t');
      const desc = p[2];
      const named = p[5] || p[4] || '';
      if (!out[cur][named]) out[cur][named] = [];
      out[cur][named].push(desc);
    }
  }
  return out;
}

const rows = {};
for (const mc of versions) {
  try {
    const tiny = parseTiny(await getTiny(mc));
    const r = {};
    for (const [cls, methods] of Object.entries(WATCH)) {
      const c = tiny[cls];
      if (!c) { r[cls] = null; continue; }
      const m = Object.create(null);
      for (const name of methods) m[name] = (c[name] || []).sort();
      r[cls] = m;
    }
    rows[mc] = r;
    process.stdout.write(`.`);
  } catch (e) {
    rows[mc] = { error: e.message };
    process.stdout.write(`\n${mc} 失败 ${e.message}`);
  }
  await new Promise(r => setTimeout(r, 150));
}
console.log('');

// 归纳：每个「方法 → 描述符集合」的变化点
const splits = [];
for (const [cls, methods] of Object.entries(WATCH)) {
  for (const name of methods) {
    let prev = null;
    const changes = [];
    for (const mc of versions) {
      const d = rows[mc]?.[cls]?.[name];
      const key = d ? JSON.stringify(d) : 'MISSING';
      if (prev !== null && key !== prev) changes.push(`${mc}:${key}`);
      prev = key;
    }
    if (changes.length) splits.push({ sig: `${cls}#${name}`, first: JSON.stringify(rows[versions[0]]?.[cls]?.[name]), changes });
  }
}

fs.writeFileSync(path.join(root, 'logs/build/api-splits.json'), JSON.stringify({ rows, splits }, null, 2));
console.log('\n=== 系列内部签名变化 ===');
for (const s of splits) {
  console.log(`${s.sig}\n   起始 ${s.first}\n   ${s.changes.join('\n   ')}`);
}
