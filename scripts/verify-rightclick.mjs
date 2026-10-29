#!/usr/bin/env node
/**
 * 右键实测验收：真起游戏 → 自动进存档 → 聊天栏给物品 → 真右击 → 看日志。
 *
 * 用法：
 *   node scripts/verify-rightclick.mjs <实例> [--world 新的世界] [--timeout 190] [--game testgame]
 *
 * 为什么要它：之前的像素验收走 MCVER_OPEN_GUI 自检钩子（进标题界面自动开），
 * 压根不经过右键，结果右键彻底不工作也没有任何用例失败。这个探针走用户真实路径。
 *
 * 前置：实例 mods/ 里要有 jar（scripts\put-mod.mjs），存档要存在
 * （探针用 --quickPlaySingleplayer 直接进世界，实例里没有这个存档会失败）。
 */
import { spawn, execFileSync } from 'node:child_process';
import path from 'node:path';
import fs from 'node:fs';

const root = path.resolve(import.meta.dirname, '..');

function arg(name, dflt) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}

const instance = process.argv[2];
if (!instance || instance.startsWith('--')) {
  console.error('用法: node scripts/verify-rightclick.mjs <实例> [--world 新的世界] [--timeout 190]');
  process.exit(2);
}
const world = arg('world', '新的世界');
const timeout = arg('timeout', '190');
const gameDir = arg('game', 'testgame');

const java = [
  'D:\\Program Files\\Java\\jdk-21.0.7\\bin\\java.exe',
  'javaw.exe',
].find((p) => fs.existsSync(p)) || 'java';

const cp = path.join(root, 'tools', 'probe', 'out');
if (!fs.existsSync(path.join(cp, 'dev', 'mcver', 'probe', 'ProbeRightClick.class'))) {
  console.error(`缺 ${cp}\\dev\\mcver\\probe\\ProbeRightClick.class —— 先跑 scripts\\build-probe.ps1`);
  process.exit(2);
}

const args = ['-cp', cp, 'dev.mcver.probe.ProbeRightClick', gameDir, instance, timeout, world];

// 前置清理：上一轮被强行掐断的游戏进程可能还活着、还攥着存档的 session.lock，
// 于是这一轮 quickPlay 会报「另一个程序已锁定文件的一部分」→ 干等标题界面假 FAIL。
// 只杀带 Minecraft 窗口的 java，不碰别的 java 进程。
for (const p of listMinecraftJava()) {
  console.log(`[verify] 结束残留游戏进程 ${p.Id} (${p.Title})`);
  try { process.kill(p.Id); } catch { }
}

function listMinecraftJava() {
  try {
    const out = execFileSync('powershell', ['-NoProfile', '-Command',
      "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | ForEach-Object { \"$($_.Id)`t$($_.MainWindowTitle)\" }"],
      { encoding: 'utf8' });
    return out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean).map((s) => {
      const [id, ...rest] = s.split('\t');
      return { Id: Number(id), Title: rest.join('\t') };
    });
  } catch { return []; }
}

console.log(`[verify] ${java} ${args.join(' ')}`);

// quickPlay 有个固有竞态：世界可能在资源重载完成前就开始 tick。
// 1.21.4 上表现为「鱿鱼被美西螈咬死 → 掉落表要走 LootModifierManager → 还没加载完 → 服务器线程崩」，
// 这是 Forge 自己的 bug，跟我们的 mod 无关。撞上了就重试一次，别把噪声当结论。
const MAX_ATTEMPTS = 2;

function run(attempt) {
  const startedAt = Date.now();
  const child = spawn(java, args, { cwd: root, stdio: 'inherit' });
  child.on('exit', (code) => {
    if (code === 0) {
      console.log('[verify] PASS 右键能打开启动器');
      process.exit(0);
    }
    if (attempt < MAX_ATTEMPTS && crashedAt(startedAt)) {
      console.log(`[verify] 游戏自己崩了（quickPlay 竞态，不是 mod 的问题），重试 ${attempt + 1}/${MAX_ATTEMPTS} ...`);
      setTimeout(() => run(attempt + 1), 4000);
      return;
    }
    console.log('[verify] FAIL');
    process.exit(code ?? 1);
  });
}

/** 本轮启动之后产生的日志里有没有 “Game crashed”。 */
function crashedAt(since) {
  try {
    const logDir = path.join(root, gameDir, 'versions', instance, 'logs');
    const files = fs.readdirSync(logDir)
      .filter((f) => f.startsWith('mcver-launch-'))
      .map((f) => path.join(logDir, f))
      .filter((p) => fs.statSync(p).mtimeMs >= since - 1000);
    return files.some((p) => {
      const txt = fs.readFileSync(p, 'utf8');
      return /Game crashed|A fatal error has been detected by the Java Runtime/.test(txt);
    });
  } catch { return false; }
}

run(1);
