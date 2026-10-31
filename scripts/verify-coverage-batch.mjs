// 批量像素验收：新补齐的那批「版本×加载器」逐个装上、真窗口抓图、按项目既有判据判定。
//
//   node scripts/verify-coverage-batch.mjs              # 跑全部
//   node scripts/verify-coverage-batch.mjs 1.16.4       # 只跑名字里含这个子串的
//
// 判据和 verify-gui-one.mjs 一致：真进程 → 日志出现 screen=MAIN chrome → PrintWindow 抓真窗口
// → 数 #C6C6C6 像素 > 10000、中心点 C6C6C6、children 17、窗口 870x519。
//
// 两个坑（本项目踩过）：
//   ① `testgame/mods` 是**共享**目录，里面残留别人的 jar 会让同 MC 版本的另一个加载器也加载它
//      → 同 mod id 两份，直接崩。所以每一步先把这个目录里的 watercraftlauncher-*.jar 清掉。
//   ② 日志要等进程退出再读（游戏活着时 log4j 独占写）；verify-gui-one 内部已经处理，这里只读它的 JSON。
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, unlinkSync, copyFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const DIST = join(ROOT, 'dist');
const TESTGAME = join(ROOT, 'testgame');

// [实例目录名, dist 里的 jar 名]
const PAIRS = [
  // ---- Forge（补齐的补丁版本）
  ['1.15-forge-29.0.4', 'watercraftlauncher-forge-1.0.0-mc1.15.jar'],
  ['1.15.1-forge-30.0.51', 'watercraftlauncher-forge-1.0.0-mc1.15.1.jar'],
  ['1.16.1-forge-32.0.108', 'watercraftlauncher-forge-1.0.0-mc1.16.1.jar'],
  ['1.16.2-forge-33.0.61', 'watercraftlauncher-forge-1.0.0-mc1.16.2.jar'],
  // 1.16.3 / 1.16.4：本机环境阻塞 —— 空实例（不装本 mod）跑同样崩n  // NoSuchMethodError: sun.security.util.ManifestEntryVerifier.<init>(Manifest)（ModLauncher 与这个 JDK 8u431 不兼容），已做对照实验。n  // 产物仍在 dist，待换 JDK/ModLauncher 组合再验。n  // ['1.16.3-forge-34.1.42', 'watercraftlauncher-forge-1.0.0-mc1.16.3.jar'],
  // ['1.16.4-forge-35.1.37', 'watercraftlauncher-forge-1.0.0-mc1.16.4.jar'],
  ['1.18-forge-38.0.17', 'watercraftlauncher-forge-1.0.0-mc1.18.jar'],
  ['1.18.1-forge-39.1.2', 'watercraftlauncher-forge-1.0.0-mc1.18.1.jar'],
  ['1.19-forge-41.1.0', 'watercraftlauncher-forge-1.0.0-mc1.19.jar'],
  ['1.19.1-forge-42.0.9', 'watercraftlauncher-forge-1.0.0-mc1.19.1.jar'],
  ['1.19.2-forge-43.5.2', 'watercraftlauncher-forge-1.0.0-mc1.19.2.jar'],
  ['1.19.3-forge-44.1.23', 'watercraftlauncher-forge-1.0.0-mc1.19.3.jar'],
  ['1.20-forge-46.0.14', 'watercraftlauncher-forge-1.0.0-mc1.20.jar'],
  // ---- NeoForge（补齐的全部）
  // NeoForge 20.1 不单独出货：它的 loader 依赖 id 是 orge，直接用 Forge 1.20.1 那个 jar
  ['1.20.1-forge-47.1.106', 'watercraftlauncher-forge-1.0.0-mc1.20.1.jar'],
  ['neoforge-20.3.8-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.20.3.jar'],
  ['neoforge-20.5.21-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.20.5.jar'],
  ['neoforge-21.0.167', 'watercraftlauncher-neoforge-1.0.0-mc1.21.jar'],
  ['neoforge-21.2.1-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.21.2.jar'],
  ['neoforge-21.6.20-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.21.6.jar'],
  ['neoforge-21.7.25-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.21.7.jar'],
  ['neoforge-21.8.57', 'watercraftlauncher-neoforge-1.0.0-mc1.21.8.jar'],
  ['neoforge-21.9.16-beta', 'watercraftlauncher-neoforge-1.0.0-mc1.21.9.jar'],
  ['neoforge-21.10.64', 'watercraftlauncher-neoforge-1.0.0-mc1.21.10.jar'],
];

const filter = process.argv[2];
const todo = filter ? PAIRS.filter(([i, j]) => (i + j).includes(filter)) : PAIRS;

function cleanMods(dir) {
  if (!existsSync(dir)) return;
  for (const f of readdirSync(dir)) {
    if (/^watercraftlauncher-.*\.jar$/.test(f)) unlinkSync(join(dir, f));
  }
}

const rows = [];
for (const [inst, jar] of todo) {
  const instDir = join(TESTGAME, 'versions', inst);
  if (!existsSync(instDir)) { rows.push({ inst, jar, ok: false, note: '实例不存在' }); console.log(`SKIP ${inst}（实例不存在）`); continue; }
  const instMods = join(instDir, 'mods');
  mkdirSync(instMods, { recursive: true });
  cleanMods(join(TESTGAME, 'mods'));
  cleanMods(instMods);
  copyFileSync(join(DIST, jar), join(instMods, jar));

  const t0 = Date.now();
  const r = spawnSync(process.execPath, [join(ROOT, 'scripts', 'verify-gui-one.mjs'), inst, '--timeout', '240'], {
    encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: 20 * 60 * 1000,
  });
  const out = (r.stdout || '') + (r.stderr || '');
  const m = out.match(/\{[\s\S]*\}/);
  let res = null;
  try { res = m ? JSON.parse(m[0]) : null; } catch { /* 保持 null */ }
  const secs = Math.round((Date.now() - t0) / 1000);
  if (res && res.ok) {
    rows.push({ inst, jar, ok: true, center: res.center, hits: res.hits, children: res.children, size: res.size, secs });
    console.log(`PASS ${inst.padEnd(24)} ${String(secs).padStart(4)}s  center=${res.center} hits=${res.hits} children=${res.children} ${res.size}`);
  } else {
    rows.push({ inst, jar, ok: false, note: res ? 'ok=false' : '没拿到结果', secs, tail: out.slice(-400) });
    console.log(`FAIL ${inst.padEnd(24)} ${String(secs).padStart(4)}s  ${(res ? JSON.stringify(res) : out.slice(-200)).slice(0, 200)}`);
  }
}

const pass = rows.filter((x) => x.ok).length;
console.log(`\n=== 汇总: ${pass}/${rows.length} 通过 ===`);
writeFileSync(join(ROOT, 'logs', 'coverage-verify.json'), JSON.stringify(rows, null, 2), 'utf8');
process.exit(pass === rows.length ? 0 : 1);
