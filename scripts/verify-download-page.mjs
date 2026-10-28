// 验收「下载页」用的小包装：把 MCVER_TAB / MCVER_CAT 用 Unicode 干净地传给游戏。
//
// 为什么不在 PowerShell 里 $env:MCVER_CAT='愚人节'：PowerShell → node 这一步会把中文
// 变成 GBK 字节，游戏里读完就对不上「愚人节」，页签永远停在默认的「正式」。
// 在 node 里设 process.env 再 spawn，Windows 走 UTF-16，中文没问题。
//
//   node scripts/verify-download-page.mjs <实例>                    # 默认「正式」页签
//   node scripts/verify-download-page.mjs <实例> --cat 愚人节
//
// 注意：这里不用像素判据。面板中心在下载页上落在列表按钮上（灰/蓝），
// 所以看的是 children=23（搜索框 + 4 页签 + 9 行 + 2 翻页 + 刷新 + 6 导航）
// 以及日志里的「分类=X 共=N 个」。
import { execFileSync } from 'node:child_process';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const argv = process.argv.slice(2);
const inst = argv.find((a) => !a.startsWith('--'));
const val = (k, d) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : d; };
const cat = val('--cat', null);
const timeout = val('--timeout', '240');

const env = { ...process.env, MCVER_TAB: '下载', MCVER_OPEN_GUI: '1' };
if (cat) env.MCVER_CAT = cat;

try {
  const out = execFileSync(process.execPath, [
    join(ROOT, 'scripts', 'verify-gui-one.mjs'), inst, '--timeout', timeout,
  ], { cwd: ROOT, encoding: 'utf8', env, maxBuffer: 16 * 1024 * 1024 });
  console.log(out.trim());
} catch (e) {
  console.log(String(e.stdout || '') + String(e.stderr || ''));
  process.exit(1);
}
