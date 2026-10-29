#!/usr/bin/env node
/**
 * 验收「点下载页分类页签 → 列表自动刷新」。
 *
 *   node scripts/verify-download-tab.mjs <实例> [--tab 快照] [--timeout 240]
 *
 * 为什么不能只看 children / 像素：那只能证明「界面画出来了」。这个脚本走真实路径 ——
 * 自动打开下载页 → 从日志里的布局算出页签坐标 → 真鼠标点一下 → 只认点击**之后**
 * 新出现的 `mcver: 分类=X 共=N 个`。
 *
 * MCVER_CAT / MCVER_TAB 用中文，必须在 node 里设 process.env 再 spawn
 * （PowerShell 传中文会被转 GBK，游戏里对不上，踩过）。
 */
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const tabArtifact = path.join(root, 'tools', 'probe', 'out', 'dev', 'mcver', 'probe', 'ProbeGuiClick.class');

function arg(name, dflt) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}

const instance = process.argv[2];
if (!instance || instance.startsWith('--')) {
  console.error('用法: node scripts/verify-download-tab.mjs <实例> [--tab 快照] [--timeout 240]');
  process.exit(2);
}
const tab = arg('tab', '快照');
const timeout = arg('timeout', '240');
const gameDir = arg('game', 'testgame');

// 顺序必须和 VersionList.CAT_LABELS 一致
const TABS = ['正式', '快照', '远古', '愚人节'];
const idx = TABS.indexOf(tab);
if (idx < 0) {
  console.error(`--tab 只能是 ${TABS.join(' / ')}`);
  process.exit(2);
}

if (!fs.existsSync(tabArtifact)) {
  console.error(`缺 ${tabArtifact} —— 先跑 scripts\\build-probe.ps1`);
  process.exit(2);
}

const java = ['D:\\Program Files\\Java\\jdk-21.0.7\\bin\\java.exe', 'javaw.exe']
  .find((p) => fs.existsSync(p)) || 'java';

// 上一轮残留的游戏会攥着日志/窗口，先清掉
try {
  const out = execFileSync('powershell', ['-NoProfile', '-Command',
    "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like 'Minecraft*' } | ForEach-Object { $_.Id }"],
    { encoding: 'utf8' });
  for (const id of out.split(/\s+/).filter(Boolean)) {
    console.log(`[verify] 结束残留游戏进程 ${id}`);
    try { process.kill(Number(id)); } catch { }
  }
} catch { }

const env = { ...process.env, MCVER_OPEN_GUI: '1', MCVER_TAB: '下载' };
const args = ['-cp', path.join(root, 'tools', 'probe', 'out'), 'dev.mcver.probe.ProbeGuiClick',
  gameDir, instance, timeout, '下载', String(idx), tab];

console.log(`[verify] ${java} ${args.join(' ')}`);
const child = spawn(java, args, { cwd: root, stdio: 'inherit', env });
child.on('exit', (code) => {
  console.log(code === 0 ? `[verify] PASS 点「${tab}」页签后列表自动刷新` : '[verify] FAIL');
  process.exit(code ?? 1);
});
