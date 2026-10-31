// 由「声明的 MC 范围」算出产物名里那段 mc<覆盖范围>。
//
//   import { labelFor } from './mc-range-label.mjs';
//   labelFor('>=1.21 <1.22')      -> '1.21-1.21.11'
//   labelFor('[1.20.1,1.20.2)')   -> '1.20.1'
//   labelFor('1.7.2')             -> '1.7.2'
//
// 规则：把范围展开成**真实存在的正式版**（用 verification/mc-versions.json 这份快照），
// 只有一个版本就写版本号，多个就写「最早的-最晚的」。这样文件名本身就说明了覆盖范围，
// 不用再去翻 fabric.mod.json 才能知道 mc1.21 到底盖到哪。
//
// 注意：上界是**开区间**语义（`<1.22` 不含 1.22）；版本清单是快照，Mojang 出新版本后
// 文件名不会自动变长 —— 需要 `node scripts/mc-range-label.mjs --refresh` 更新快照并重建。
//
// CLI：
//   node scripts/mc-range-label.mjs ">=1.21 <1.22"   # 打印 label
//   node scripts/mc-range-label.mjs --refresh        # 从 BMCLAPI 拉最新版本清单覆盖快照
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SNAPSHOT = join(ROOT, 'verification', 'mc-versions.json');

export function loadVersions() {
  if (!existsSync(SNAPSHOT)) {
    throw new Error('缺版本快照：' + SNAPSHOT + '\n跑 node scripts/mc-range-label.mjs --refresh 生成');
  }
  return JSON.parse(readFileSync(SNAPSHOT, 'utf8')).releases;
}

const parts = (v) => v.split('.').map((x) => Number(String(x).replace(/[^0-9]/g, '')) || 0);
export function cmp(a, b) {
  const x = parts(a), y = parts(b);
  for (let i = 0; i < 3; i++) { const d = (x[i] || 0) - (y[i] || 0); if (d) return d; }
  return 0;
}

/** 把 maven 风格的范围串解析成 [lo, hiExclusive]；单个版本号原样返回 [v, v]。 */
function bounds(range) {
  const s = String(range || '').trim();
  if (!s) throw new Error('空范围');
  if (/^[\d.]+$/.test(s)) return [s, null];
  const br = /^\[\s*([\d.]+)\s*,\s*([\d.]+)\s*\)$/.exec(s);
  if (br) return [br[1], br[2]];
  const ge = />=\s*([\d.]+)/.exec(s);
  const lt = /<\s*([\d.]+)/.exec(s);
  if (!ge && !lt) throw new Error('看不懂的范围：' + s);
  return [ge ? ge[1] : null, lt ? lt[1] : null];
}

/** 范围 → 覆盖到的正式版列表（升序）。 */
export function versionsIn(range, releases = loadVersions()) {
  const [lo, hi] = bounds(range);
  if (hi === null) return [lo];
  return releases.filter((v) => (lo === null || cmp(v, lo) >= 0) && cmp(v, hi) < 0);
}

/** 范围 → 文件名里那段标签（不含 mc 前缀）。 */
export function labelFor(range, releases = loadVersions()) {
  const list = versionsIn(range, releases);
  if (!list.length) throw new Error('范围里没有任何正式版：' + range);
  return list.length === 1 ? list[0] : `${list[0]}-${list[list.length - 1]}`;
}

async function refresh() {
  const r = await fetch('https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json', {
    headers: { 'User-Agent': 'WaterCraftLauncher/1.0' },
  });
  if (!r.ok) throw new Error('拉版本清单失败: HTTP ' + r.status);
  const j = await r.json();
  const releases = j.versions.filter((v) => v.type === 'release').map((v) => v.id).sort(cmp);
  writeFileSync(SNAPSHOT, JSON.stringify({ generatedFrom: 'bmclapi2.bangbang93.com', releases }, null, 0) + '\n', 'utf8');
  console.log(`快照已更新：${releases.length} 个正式版（最新 ${releases[releases.length - 1]}）-> ${SNAPSHOT}`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const arg = process.argv[2];
  if (arg === '--refresh') await refresh();
  else if (!arg) console.error('用法: node scripts/mc-range-label.mjs "<range>" | --refresh');
  else console.log(labelFor(arg));
}
