// Fabric 全系列界面验收（Node 驱动版）：
//   - 按每个 dist jar 里 fabric.mod.json 的 "minecraft" 区间挑 jar（不靠文件名猜）
//   - 按 MC 版本从 testgame\.cache 换 fabric-api（换版本必须换，否则 loader 直接拒载）
//   - 逐个实例调 scripts/verify-gui-one.mjs（真窗口 + 像素判据）
// 用法：node scripts/verify-fabric-all.mjs [实例/版本 ...]
import { spawnSync, execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, copyFileSync, unlinkSync, mkdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const DIST = join(ROOT, 'dist');
const CACHE = join(ROOT, 'testgame', '.cache');
const apiMap = JSON.parse(readFileSync(join(ROOT, 'logs', 'build', 'fabric-api-map.json'), 'utf8'));

// [MC 版本, 实例目录名]
const PAIRS = [
  ['1.7.10', 'V1.7.10-FABRIC'],
  ['1.8', 'V1.8-FABRIC'],
  ['1.8.8', 'V1.8.8-FABRIC'],
  ['1.8.9', 'V1.8.9-FABRIC'],
  ['1.9.4', 'V1.9.4-FABRIC'],
  ['1.10.2', 'V1.10.2-FABRIC'],
  ['1.11.2', 'V1.11.2-FABRIC'],
  ['1.12.2', 'V1.12.2-FABRIC'],
  ['1.13.2', 'V1.13.2-FABRIC'],
  ['1.14', 'V1.14-FABRIC'],
  ['1.14.4', 'V1.14.4-FABRIC'],
  ['1.15.2', 'V1.15.2-FABRIC'],
  ['1.16', 'fabric-loader-0.20.1-1.16'],
  ['1.16.5', 'V1.16.5-FABRIC'],
  ['1.17', 'V1.17-FABRIC'],
  ['1.17.1', 'fabric-loader-0.20.1-1.17.1'],
  ['1.18.2', 'V1.18.2-FABRIC'],
  ['1.19', 'fabric-loader-0.20.1-1.19'],
  ['1.19.4', 'V1.19.4-FABRIC'],
  ['1.20.1', 'V1.20.1-FABRIC'],
  ['1.20.4', 'V1.20.4-FABRIC'],
  ['1.20.6', 'V1.20.6-FABRIC'],
  ['1.21.1', 'V1.21.1-FABRIC'],
  ['1.21.4', 'V1.21.4-FABRIC'],
  ['1.21.11', 'v12111'],
  ['26.1', 'V26.1-FABRIC'],
];

function tuple(v) {
  return v.split('.').map((x) => (/^\d+$/.test(x) ? Number(x) : 0)).concat([0, 0, 0]).slice(0, 3);
}
function cmp(a, b) {
  const x = tuple(a), y = tuple(b);
  for (let i = 0; i < 3; i++) if (x[i] !== y[i]) return x[i] - y[i];
  return 0;
}
function satisfies(mc, range) {
  // 本仓库写的三种形式：">=A <B"、">=A"、精确 "A"
  if (!range) return false;
  if (/^[\d.]+$/.test(range.trim())) return cmp(mc, range.trim()) === 0;
  const m = range.match(/^>=\s*([\d.]+)\s*(?:<\s*([\d.]+))?$/);
  if (!m) return false;
  if (cmp(mc, m[1]) < 0) return false;
  if (m[2] && cmp(mc, m[2]) >= 0) return false;
  return true;
}

// jar 命令用来读 fabric.mod.json；Windows 上 PATH 里不一定有，这里自己补上
const JAVA_HOME = process.env.JAVA_HOME || 'D:\\Program Files\\Java\\jdk-21.0.7';
process.env.PATH = join(JAVA_HOME, 'bin') + ';' + process.env.PATH;

function readRange(jarPath) {
  const tmp = join(ROOT, 'logs', 'build', '.fmj');
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(tmp, { recursive: true });
  execFileSync('jar', ['xf', jarPath, 'fabric.mod.json'], { cwd: tmp });
  const j = JSON.parse(readFileSync(join(tmp, 'fabric.mod.json'), 'utf8'));
  return j.depends && j.depends.minecraft;
}

const ranges = readdirSync(DIST)
  .filter((f) => f.endsWith('.jar') && !f.includes('-sources'))
  .map((f) => ({ file: f, range: readRange(join(DIST, f)) }));

function jarFor(mc) {
  const hit = ranges.filter((r) => satisfies(mc, r.range));
  if (!hit.length) throw new Error(`没有 dist jar 声明支持 ${mc}`);
  // 区间越窄越具体（1.19.3 那份优先于 1.19）
  hit.sort((a, b) => cmp(a.range.replace(/^>=|<\s*/g, '').split(/\s+/)[0], b.range.replace(/^>=|<\s*/g, '').split(/\s+/)[0]));
  return join(DIST, hit[hit.length - 1].file);
}

const only = process.argv.slice(2);
const list = only.length ? PAIRS.filter(([mc, id]) => only.includes(mc) || only.includes(id)) : PAIRS;

const results = [];
for (const [mc, inst] of list) {
  const instDir = join(ROOT, 'testgame', 'versions', inst);
  if (!existsSync(instDir)) {
    console.log(`[skip] ${mc} (${inst}) 实例不存在`);
    results.push({ mc, inst, ok: false, note: 'no instance' });
    continue;
  }
  const mods = join(instDir, 'mods');
  mkdirSync(mods, { recursive: true });

  const jar = jarFor(mc);
  // 1.13.2 及更早（Legacy Fabric）不需要 fabric-api：那代只有 legacy-fabric-api，
  // 包名是 net.legacyfabric.* + ornithe C_xxxx 命名，跟本工程的 class_xxxx intermediary 对不上
  const legacy = cmp(mc, '1.14') < 0;
  const api = apiMap[mc];
  const NEED_API = !legacy;
  if (NEED_API && !api) throw new Error(`fabric-api-map.json 里没有 ${mc}`);
  const apiSrc = api ? join(CACHE, api.file) : null;
  if (NEED_API && !existsSync(apiSrc)) throw new Error(`缺 fabric-api 缓存 ${apiSrc}`);

  for (const f of readdirSync(mods)) {
    if (f.startsWith('watercraftlauncher-') || f.startsWith('fabric-api-') || f.startsWith('legacy-fabric-api-')) {
      unlinkSync(join(mods, f));
    }
  }
  copyFileSync(jar, join(mods, jar.split(/[\\/]/).pop()));
  if (NEED_API) copyFileSync(apiSrc, join(mods, api.file));

  console.log(`\n=== ${mc} (${inst})  ${jar.split(/[\\/]/).pop()}${NEED_API ? ' + ' + api.file : ' (无 fabric-api)'}`);
  const r = spawnSync(process.execPath, [join(ROOT, 'scripts', 'verify-gui-one.mjs'), inst], {
    cwd: ROOT,
    encoding: 'utf8',
    timeout: 300000,
  });
  const out = (r.stdout || '') + (r.stderr || '');
  let res = null;
  try {
    res = JSON.parse(out.slice(out.indexOf('{')));
  } catch {
    /* 下面统一记失败 */
  }
  if (res) {
    console.log(`  ${res.ok ? 'PASS' : 'FAIL'} center=${res.center} hits=${res.hits} children=${res.children}`);
    results.push({ mc, inst, ...res });
  } else {
    console.log('  FAIL 解析不出结果:\n' + out.split('\n').slice(-8).join('\n'));
    results.push({ mc, inst, ok: false, note: 'no json' });
  }
}

console.log('\n==== 汇总 ====');
for (const r of results) {
  console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.mc.padEnd(8)} ${r.inst.padEnd(30)} ${r.center || r.note || ''} hits=${r.hits || ''} children=${r.children || ''}`);
}
const bad = results.filter((r) => !r.ok);
console.log(bad.length ? `\n失败 ${bad.length} 个` : '\n全部通过');
process.exit(bad.length ? 1 : 0);
