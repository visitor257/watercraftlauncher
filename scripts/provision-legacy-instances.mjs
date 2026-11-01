// 给「老 target」补装实例：build-neoforge 的 TARGETS 里那些 V1.20.1-FORGE 之类的老实例
// 在换机器时被清掉了，重建产物前必须把它们装回来。
//
//   node scripts/provision-legacy-instances.mjs            # 缺什么装什么（幂等）
//   node scripts/provision-legacy-instances.mjs --list     # 只看清单
//
// 做法：ProbeInstall 装出 <mc>-forge-<ver> 这种标准命名的实例，然后**复制**一份成 target
// 期望的名字（V<mc>-FORGE / V<mc>-NEOFORGE），并把 json 里的 "id" 改掉。
// 为什么不直接改 TARGETS 里的 instance：那是共享配置，改了会影响已有产物与其他人。
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, writeFileSync, cpSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const JDK = process.env.MCVER_JDK21 || 'D:\\Program Files\\Java\\jdk-21';
const JAVA = join(JDK, 'bin', 'java.exe');
const TESTGAME = join(ROOT, 'testgame');
const PROBE_OUT = join(ROOT, 'tools', 'probe', 'out');
const VERS = join(TESTGAME, 'versions');

// [目标实例名, MC 版本, 加载器, 加载器版本]
const NEED = [
  ['V1.14.4-FORGE', '1.14.4', 'FORGE', '28.2.30'],
  ['1.15.2-forge-31.2.62', '1.15.2', 'FORGE', '31.2.62'],
  ['V1.16.5-FORGE', '1.16.5', 'FORGE', '36.2.42'],
  ['V1.17.1-FORGE', '1.17.1', 'FORGE', '37.1.1'],
  ['V1.18.2-FORGE', '1.18.2', 'FORGE', '40.3.12'],
  ['V1.19.4-FORGE', '1.19.4', 'FORGE', '45.4.5'],
  ['V1.20.1-FORGE', '1.20.1', 'FORGE', '47.4.28'],
  ['V1.20.2-FORGE', '1.20.2', 'FORGE', '48.1.0'],
  ['V1.20.4-FORGE', '1.20.4', 'FORGE', '49.2.13'],
  ['V1.20.6-FORGE', '1.20.6', 'FORGE', '50.2.14'],
  ['V1.21.1-FORGE', '1.21.1', 'FORGE', '52.1.20'],
  ['V1.21.4-FORGE', '1.21.4', 'FORGE', '54.1.22'],
  ['V1.20.2-NEOFORGE', '1.20.2', 'NEOFORGE', '20.2.93'],
  ['V1.20.4-NEOFORGE', '1.20.4', 'NEOFORGE', '20.4.253'],
  ['V1.20.6-NEOFORGE', '1.20.6', 'NEOFORGE', '20.6.144'],
  ['V1.21.1-NEOFORGE', '1.21.1', 'NEOFORGE', '21.1.265'],
  ['V1.21.4-NEOFORGE', '1.21.4', 'NEOFORGE', '21.4.161'],
];

const argv = process.argv.slice(2);
const missing = NEED.filter(([inst]) => !existsSync(join(VERS, inst, inst + '.json')));
console.log(`清单 ${NEED.length} 个，缺 ${missing.length} 个`);
for (const [inst, mc, kind, ver] of missing) console.log(`  - ${inst}  (${mc} ${kind} ${ver})`);
if (argv.includes('--list')) process.exit(0);

function newestVersionDir(since) {
  const cands = readdirSync(VERS).map((n) => ({ n, t: statSync(join(VERS, n)).mtimeMs })).filter((x) => x.t >= since - 1000);
  cands.sort((a, b) => b.t - a.t);
  return cands.length ? cands[0].n : null;
}

let ok = 0, fail = 0;
for (const [inst, mc, kind, ver] of missing) {
  console.log(`\n== ${inst}  (${mc} ${kind} ${ver})`);
  const t0 = Date.now();
  const r = spawnSync(JAVA, ['-cp', PROBE_OUT, 'dev.mcver.probe.ProbeInstall', TESTGAME, mc, kind, ver, 'NONE', '-', '-', '1'],
    { encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
  const out = (r.stdout || '') + (r.stderr || '');
  const m = /resultVersionId=(\S+)/.exec(out);
  if (r.status !== 0 || !m || m[1] === 'null') {
    console.log('   安装失败：' + out.slice(-400));
    fail++;
    continue;
  }
  const made = m[1];
  const src = join(VERS, made);
  const dst = join(VERS, inst);
  if (made !== inst) {
    cpSync(src, dst, { recursive: true });
    // 改 json 里的 id，让它和目录名一致（探针/启动器按 id 找目录）
    const jp = join(dst, made + '.json');
    if (existsSync(jp)) {
      const j = JSON.parse(readFileSync(jp, 'utf8'));
      j.id = inst;
      writeFileSync(join(dst, inst + '.json'), JSON.stringify(j, null, 2), 'utf8');
      try { execFileSync('cmd', ['/c', 'del', jp]); } catch { /* 留着也无害 */ }
    }
  }
  const ok2 = existsSync(join(dst, inst + '.json'));
  console.log(`   ${ok2 ? 'OK' : 'FAIL'}  ${made} -> ${inst}  (${Math.round((Date.now() - t0) / 1000)}s)`);
  if (ok2) ok++; else fail++;
}
console.log(`\n完成：成功 ${ok}，失败 ${fail}`);
process.exit(fail ? 1 : 0);
