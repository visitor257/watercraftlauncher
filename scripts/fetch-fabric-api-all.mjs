// 给「一个 jar 盖一个 1.xx 系列」准备 fabric-api。
//
// 每个补丁版本能用的 fabric-api 版本不一定一样（1.16 / 1.16.1 和 1.16.5 就不完全通用），
// 所以按 Modrinth 的 game_versions 反查，取该 MC 版本最新的 release，下载到 testgame\.cache，
// 并把结果写成 JSON 给 verify-gui.ps1 用。
import fs from 'node:fs';
import path from 'node:path';

const root = 'D:/Development/watercraftlauncher';
const cache = path.join(root, 'testgame/.cache');
fs.mkdirSync(cache, { recursive: true });

const versions = [
  '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
  '1.19', '1.19.1', '1.19.2', '1.19.3', '1.19.4',
  '1.18', '1.18.1', '1.18.2',
  '1.17', '1.17.1',
  '1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5',
];

async function findApi(mc) {
  const url = `https://api.modrinth.com/v2/project/fabric-api/version?game_versions=${encodeURIComponent(JSON.stringify([mc]))}`;
  const r = await fetch(url, { headers: { 'User-Agent': 'watercraftlauncher-verify/1.0' } });
  if (!r.ok) throw new Error(`Modrinth ${mc} -> HTTP ${r.status}`);
  const list = await r.json();
  if (!list.length) throw new Error(`Modrinth 上没有支持 ${mc} 的 fabric-api`);
  // 优先 release 通道，其次新的
  const rel = list.filter(v => v.version_type === 'release');
  const pick = (rel.length ? rel : list)[0];
  return { version: pick.version_number, url: pick.files[0].url, file: pick.files[0].filename };
}

const out = {};
for (const mc of versions) {
  try {
    const api = await findApi(mc);
    const dest = path.join(cache, api.file);
    if (!fs.existsSync(dest) || fs.statSync(dest).size === 0) {
      const r = await fetch(api.url);
      if (!r.ok) throw new Error(`下载失败 HTTP ${r.status}`);
      fs.writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
      console.log(`${mc}  ${api.version}  -> 下载 ${(fs.statSync(dest).size / 1048576).toFixed(1)}MB`);
    } else {
      console.log(`${mc}  ${api.version}  -> 已有`);
    }
    out[mc] = { version: api.version, file: api.file };
  } catch (e) {
    console.log(`${mc}  失败: ${e.message}`);
    out[mc] = null;
  }
  await new Promise(r => setTimeout(r, 250));
}

const jsonPath = path.join(root, 'logs/build/fabric-api-map.json');
fs.writeFileSync(jsonPath, JSON.stringify(out, null, 2));
console.log(`\n写入 ${jsonPath}`);
const bad = Object.entries(out).filter(([, v]) => !v);
if (bad.length) console.log('没拿到：' + bad.map(([k]) => k).join(', '));
