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
import { spawn } from 'node:child_process';
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
console.log(`[verify] ${java} ${args.join(' ')}`);

const child = spawn(java, args, { cwd: root, stdio: 'inherit' });
child.on('exit', (code) => {
  console.log(code === 0 ? '[verify] PASS 右键能打开启动器' : '[verify] FAIL');
  process.exit(code ?? 1);
});
