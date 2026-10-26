// 查 1.21.x 全系（1.21 ~ 1.21.11）每版的 yarn build + fabric-api 版本。
// 目的：为「一个 jar 盖整个 1.21 系列」准备编译参数，并逐版本跑硬编译看编译器判决。
import fs from 'node:fs';

const VERSIONS = [
  '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5',
  '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
];

async function j(url) {
  const r = await fetch(url, { headers: { 'User-Agent': 'watercraft-launcher-dev/1.0' } });
  if (!r.ok) throw new Error(`${r.status} ${url}`);
  return r.json();
}

const out = [];
for (const v of VERSIONS) {
  let yarn = null, api = null, note = '';
  try {
    const ys = await j(`https://meta.fabricmc.net/v2/versions/yarn/${v}`);
    if (ys.length) yarn = ys[0].version;
    else note += 'no-yarn ';
  } catch (e) { note += 'yarn-fail '; }

  try {
    const q = encodeURIComponent(JSON.stringify([v]));
    const vs = await j(`https://api.modrinth.com/v2/project/fabric-api/version?game_versions=${q}`);
    // 取第一个 releases 优先
    const rel = vs.find(x => x.version_type === 'release') || vs[0];
    if (rel) api = rel.version_number;
    else note += 'no-api ';
  } catch (e) { note += 'api-fail '; }

  out.push({ v, yarn, api, note: note.trim() });
  console.log(`${v.padEnd(9)} yarn=${String(yarn).padEnd(22)} api=${String(api).padEnd(24)} ${note}`);
  await new Promise(r => setTimeout(r, 300));
}

fs.writeFileSync('D:/Development/watercraftlauncher/logs/build/121-meta.json', JSON.stringify(out, null, 2));
console.log('\n写到 logs/build/121-meta.json');
