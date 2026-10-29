// 全量重建：改了共享页面代码（mod/src/main/java）之后，把 dist 里所有 jar 刷一遍。
//
//   node scripts/rebuild-all.mjs            # 全部
//   node scripts/rebuild-all.mjs forge      # 只跑含 "forge" 的步骤（子串匹配）
//   node scripts/rebuild-all.mjs --list
//
// 每步单独跑、单独记日志到 logs/build/rebuild-<name>.log，一步挂了不影响后面的，
// 最后打一张总表。**不**自动重试、**不**自动改参数 —— 失败就在表里显红，由人判断。
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, statSync, existsSync, readdirSync, copyFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const LOGS = join(ROOT, 'logs', 'build');
mkdirSync(LOGS, { recursive: true });

const NODE = process.execPath;
const PS = 'powershell';

// ---- Legacy Fabric（javac + tiny-remapper）：1.7 ~ 1.13.2
// 1.7~1.7.8 用 compat/1.7.2（那几代的 yarn 没映射 Screen/ButtonWidget 成员，
// 中间名 id 实测完全一致，所以一份适配层能盖它们）；1.7.10 的映射是全的，单独一份。
// 1.7.9 缺 yarn（meta 上没有）+ loader 自己 remap 就直接 Unfixable conflicts，做不了。
const fabricLegacy = [
  ...['1.7', '1.7.1', '1.7.2', '1.7.3', '1.7.4', '1.7.5', '1.7.6', '1.7.7', '1.7.8'].map((v) => {
    // 早期手工建的实例叫 V<mc>-FABRIC，脚本建的叫 fabric-loader-0.20.1-<mc>
    const inst = v === '1.7.2' ? 'V1.7.2-FABRIC' : `fabric-loader-0.20.1-${v}`;
    return [`fabric-${v}`, ['scripts/build-1132.mjs', v, '--instance', inst]];
  }),
  ['fabric-1.7.10', ['scripts/build-1132.mjs', '1.7.10', '--instance', 'V1.7.10-FABRIC']],
  ['fabric-1.8.9', ['scripts/build-1132.mjs', '1.8.9', '--instance', 'V1.8.9-FABRIC', '--mcrange', '>=1.8 <1.9']],
  ['fabric-1.9.4', ['scripts/build-1132.mjs', '1.9.4', '--instance', 'V1.9.4-FABRIC']],
  ['fabric-1.10.2', ['scripts/build-1132.mjs', '1.10.2', '--instance', 'V1.10.2-FABRIC']],
  ['fabric-1.11.2', ['scripts/build-1132.mjs', '1.11.2', '--instance', 'V1.11.2-FABRIC']],
  ['fabric-1.12.2', ['scripts/build-1122.mjs']],
  ['fabric-1.13.2', ['scripts/build-1132.mjs', '1.13.2', '--instance', 'V1.13.2-FABRIC']],
];

// ---- Forge / NeoForge 现代（mojmap 编 + reobf）
const forgeModern = [
  'forge-14.4', 'forge-15.2', 'forge-16.5', 'forge-17.1', 'forge-18.2', 'forge-19.4',
  'forge-20.1', 'forge-20.2', 'forge-20.4', 'forge-20.6', 'forge-21.1', 'forge-21.4',
  'neoforge-20.2', 'neoforge-20.4', 'neoforge-20.6', 'neoforge-21.1', 'neoforge-21.4',
].map((t) => [t, ['scripts/build-neoforge.mjs', t]]);

// ---- Forge 1.7.10 ~ 1.12.2（SRG 编，垫片 + overlay）
const forgeLegacy = [
  ['forge-1.7.2', ['scripts/build-172forge.mjs']],   // 要装到实例里才编得动：先 installForge
  ['forge-1.7.10', ['scripts/build-1710forge.mjs']],
  ['forge-1.8.9', ['scripts/build-srg-legacy.mjs', '--mc', '1.8.9', '--forge', '11.15.1.2318',
    '--instance', '1.8.9-forge1.8.9-11.15.1.2318-1.8.9', '--shim', 'shim189', '--overlay', 'forge102,forge189']],
  ['forge-1.9.4', ['scripts/build-srg-legacy.mjs', '--mc', '1.9.4', '--forge', '12.17.0.2317',
    '--instance', '1.9.4-forge1.9.4-12.17.0.2317-1.9.4', '--shim', 'shim1102', '--overlay', 'forge102,forge194']],
  ['forge-1.10.2', ['scripts/build-srg-legacy.mjs', '--mc', '1.10.2', '--forge', '12.18.3.2511',
    '--instance', '1.10.2-forge1.10.2-12.18.3.2511', '--shim', 'shim1102', '--overlay', 'forge102']],
  ['forge-1.11.2', ['scripts/build-srg-legacy.mjs', '--mc', '1.11.2', '--forge', '13.20.1.2588',
    '--instance', '1.11.2-forge1.11.2-13.20.1.2588', '--shim', 'shim1122', '--overlay', 'forge102,forge111']],
  ['forge-1.12.2', ['scripts/build-1122forge.mjs']],
];

const steps = [
  ...forgeLegacy,
  ...forgeModern,
  ...fabricLegacy,
  // gradle 那套放最后：它会起 daemon，跟一堆 javac 抢内存
  // -NoDaemon 是必须的：daemon 会继承 stdout，execFileSync 等 EOF 会一直挂到超时
  // （2026-10-28 实测：9 个 target 全部构建完、dist 也拷好了，步骤却一直不返回）
  ['fabric-modern', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'scripts/build-all.ps1', '-NoDaemon'], PS],
];

const argv = process.argv.slice(2);
if (argv.includes('--list')) {
  for (const [n] of steps) console.log(n);
  process.exit(0);
}
const filter = argv.find((a) => !a.startsWith('--'));
if (filter === 'publish') {
  publish();
  process.exit(0);
}
const todo = filter ? steps.filter(([n]) => n.includes(filter)) : steps;

const rows = [];
let n = 0;
for (const [name, args, bin = NODE] of todo) {
  n++;
  const logFile = join(LOGS, `rebuild-${name}.log`);
  const t0 = Date.now();
  process.stdout.write(`[${n}/${todo.length}] ${name} ... `);
  let ok = false;
  let err = '';
  try {
    const out = execFileSync(bin, args, {
      cwd: ROOT, encoding: 'utf8', timeout: 30 * 60 * 1000,
      maxBuffer: 64 * 1024 * 1024, stdio: ['ignore', 'pipe', 'pipe'],
    });
    writeFileSync(logFile, out, 'utf8');
    ok = true;
  } catch (e) {
    err = String(e.message || e);
    const body = String(e.stdout || '') + String(e.stderr || '') + '\n' + err;
    writeFileSync(logFile, body, 'utf8');
  }
  const secs = Math.round((Date.now() - t0) / 1000);
  console.log(ok ? `OK ${secs}s` : `FAIL ${secs}s`);
  rows.push({ name, ok, secs, log: `logs/build/rebuild-${name}.log` });
}

console.log('\n==== 汇总');
for (const r of rows) console.log(`${r.ok ? 'OK  ' : 'FAIL'}  ${r.name.padEnd(18)} ${String(r.secs).padStart(4)}s  ${r.ok ? '' : r.log}`);
const bad = rows.filter((r) => !r.ok);
console.log(`\n${rows.length - bad.length}/${rows.length} 成功`);
if (!bad.length) publish();
process.exit(bad.length ? 1 : 0);

/**
 * 把 modx/out/<target>/*.jar 抄到 dist。
 *
 * build-neoforge.mjs 只写 modx/out，不碰 dist（dist 是发布目录，只收成品）；
 * Fabric 那几套脚本则直接写 dist。所以全量重建后要补这一步，否则 dist 里是旧 jar。
 */
function publish() {
  const dist = join(ROOT, 'dist');
  mkdirSync(dist, { recursive: true });
  const outRoot = join(ROOT, 'modx', 'out');
  let n = 0;
  for (const dir of readdirSync(outRoot)) {
    if (/spike/i.test(dir)) continue;
    const p = join(outRoot, dir);
    if (!statSync(p).isDirectory()) continue;
    for (const f of readdirSync(p)) {
      if (!f.endsWith('.jar') || /^spike/i.test(f)) continue;
      const src = join(p, f);
      const dst = join(dist, f);
      if (existsSync(dst) && statSync(dst).mtimeMs >= statSync(src).mtimeMs) continue;
      copyFileSync(src, dst);
      n++;
    }
  }
  console.log(`publish: ${n} 个 jar -> dist`);
}
