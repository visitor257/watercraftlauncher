// 单个实例的界面级验收（loader 无关）—— Node 驱动版。
//
// 判据和 verify-gui.ps1 一样硬：真进程 → 等游戏日志出现 `screen=MAIN chrome`（面板真的画了）
// → +4s 等首帧稳定 → PrintWindow 抓真窗口 → 数面板色 #C6C6C6 的像素数 + 中心点颜色 + children 数。
// 「日志里有 screen=MAIN」不算通过（那是代码路径，不是像素）。
//
// 为什么是 Node：PowerShell 5.1 里 $null 上的方法调用、`$null -ne 0` 恒真、@() 缺失导致
// 假通过……这个项目已经在验证脚本上栽过 7 次，流程放 Node，PowerShell 只留抓图。
//
// 用法：
//   node scripts/verify-gui-one.mjs neoforge-21.1.265
//   node scripts/verify-gui-one.mjs V1.20.1-FORGE --timeout 240
// 退出码：0 = 通过；1 = 失败。

import { spawn, execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync, openSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const TESTGAME = join(ROOT, 'testgame');
const LOGS = join(ROOT, 'logs', 'build');

const argv = process.argv.slice(2);
const instance = argv.find((a) => !a.startsWith('--'));
const timeoutArg = argv.indexOf('--timeout');
const TIMEOUT = timeoutArg >= 0 ? Number(argv[timeoutArg + 1]) : 220;
if (!instance) {
  console.error('用法: node scripts/verify-gui-one.mjs <实例目录名> [--timeout 秒]');
  process.exit(2);
}

const instDir = join(TESTGAME, 'versions', instance);
if (!existsSync(instDir)) {
  console.error('没有这个实例: ' + instDir);
  process.exit(2);
}
mkdirSync(LOGS, { recursive: true });

// 实例必须开版本隔离，否则日志/options.txt 都不在实例目录里
// （踩过：日志落到 testgame/logs，永远等不到标记）
const isoMarker = join(instDir, '.mcver-isolated');
if (!existsSync(isoMarker)) writeFileSync(isoMarker, '1');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 0) 关掉无障碍引导屏（1.21.9+ 会挡在 TitleScreen 前面），没 options.txt 就建一个
const optFile = join(instDir, 'options.txt');
if (!existsSync(optFile)) writeFileSync(optFile, 'onboardAccessibility:false\nguiScale:2\n', 'utf8');
else {
  const lines = readFileSync(optFile, 'utf8').split(/\r?\n/);
  const i = lines.findIndex((l) => l.startsWith('onboardAccessibility:'));
  if (i >= 0) lines[i] = 'onboardAccessibility:false';
  else lines.push('onboardAccessibility:false');
  writeFileSync(optFile, lines.join('\n'), 'utf8');
}

// 1) 起游戏：探针的 waitFor 传一个永远不出现的串 —— 探针一旦看到标记就退出并杀掉游戏，
//    那就再也抓不到窗口了（踩过：一直拿不到窗口，实际是游戏已经被探针杀掉）。
try { execFileSync('powershell', ['-NoProfile', '-Command', 'Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force']); } catch { }

const t0 = Date.now() - 5000;
const holdLog = join(LOGS, `hold-${instance}.log`);
const out = openSync(holdLog, 'w');
const javaExe = join(process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7', 'bin', 'java.exe');
const probe = spawn(javaExe, [
  '-cp', join(ROOT, 'tools', 'probe', 'out'),
  'dev.mcver.probe.ProbeLaunch', 'testgame', instance, String(TIMEOUT), 'ZZNEVERZZ',
], { cwd: ROOT, env: { ...process.env, MCVER_OPEN_GUI: '1' }, stdio: ['ignore', out, out] });

// 2) 等本次启动的日志里出现面板绘制标记
const logDir = join(instDir, 'logs');
let gameLog = null;
const deadline = Date.now() + TIMEOUT * 1000;
while (Date.now() < deadline) {
  if (existsSync(logDir)) {
    const cands = readdirSync(logDir)
      .filter((n) => n.startsWith('mcver-launch-') && n.endsWith('.log'))
      .map((n) => { const p = join(logDir, n); return { p, m: statSync(p).mtimeMs }; })
      .filter((x) => x.m >= t0)
      .sort((a, b) => b.m - a.m);
    if (cands.length) {
      gameLog = cands[0].p;
      const txt = readFileSync(gameLog, 'utf8');
      if (txt.includes('screen=MAIN chrome')) break;
      if (txt.includes('Mod loading has failed') || txt.includes('ExceptionInInitializerError')) break;
    }
  }
  await sleep(1000);
}

if (!gameLog) {
  console.error(`[${instance}] 失败：没有本轮日志`);
  killAll();
  process.exit(1);
}

const text = readFileSync(gameLog, 'utf8');
if (!text.includes('screen=MAIN chrome')) {
  console.error(`[${instance}] 失败：日志里没有面板绘制标记（log=${gameLog}）`);
  const tail = text.split(/\r?\n/).slice(-15).join('\n');
  console.error(tail);
  killAll();
  process.exit(1);
}

// 3) 等首帧稳定（atlas / 全景背景要几秒），再抓窗口
// 坑：capture-window.ps1 偶发卡住（PrintWindow 阻塞），必须给 timeout，否则整个验收挂死却不出结论。
await sleep(4000);
const png = join(LOGS, `gui-${instance}.png`);
let shot = null;
for (let i = 0; i < 30; i++) {
  try {
    const raw = execFileSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
      join(ROOT, 'scripts', 'capture-window.ps1'), '-Out', png], { encoding: 'utf8', timeout: 15000 }).trim();
    shot = JSON.parse(raw.split(/\r?\n/).pop());
    if (shot.ok) break;
  } catch (e) {
    shot = { ok: false, reason: String(e.message || e) };
  }
  await sleep(1000);
}

// 4) 先杀进程再读日志（游戏活着时日志独占写，读不到 children=）
killAll();
await sleep(1500);
const after = readFileSync(gameLog, 'utf8');
const m = after.match(/children=(\d+)/);
const children = m ? Number(m[1]) : null;

const ok = !!(shot && shot.ok && shot.center === 'C6C6C6' && shot.hits > 10000 && children !== null);
console.log(JSON.stringify({
  instance, ok,
  center: shot && shot.center,
  hits: shot && shot.hits,
  children,
  size: shot && shot.ok ? `${shot.w}x${shot.h}` : undefined,
  reason: shot && !shot.ok ? shot.reason : undefined,
  png, log: gameLog,
}, null, 2));
process.exit(ok ? 0 : 1);

function killAll() {
  try { execFileSync('powershell', ['-NoProfile', '-Command', 'Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force']); } catch { }
  try { probe.kill(); } catch { }
}
