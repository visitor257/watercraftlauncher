// 时间基准必须在「启动游戏之前」取。
// 之前放在「窗口出现之后」：游戏日志在启动后 2~5 秒就创建，而窗口要 15~20 秒才出现，
// 于是 CreationTime 永远比基准早 10 秒以上 → 被过滤器排除 → 找不到本轮日志 → 拿不到 children=。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
if (crlf) s = s.replace(/\r\n/g, '\n');

const anchor = `    $logFile = Join-Path $logs "hold-$v.log"`;
const withStamp = `    # 时间基准取在启动之前：日志在启动后 2~5 秒就创建，窗口要 15~20 秒才出现。
    $launchedAt = Get-Date
    $logFile = Join-Path $logs "hold-$v.log"`;

const dup = `    $launchedAt = Get-Date
    $gameLog = $null`;

const a = s.indexOf(anchor);
const b = s.indexOf(dup);
if (a < 0 || b < 0) {
  console.log('MISS: anchor=' + (a >= 0) + ' dup=' + (b >= 0));
  process.exit(1);
}
if (s.includes('时间基准取在启动之前')) {
  console.log('已经改过');
} else {
  // 先删重复的那行（它在前、锚点在后，注意顺序：dup 在 anchor 之后）
  s = s.slice(0, b) + `    $gameLog = $null` + s.slice(b + dup.length);
  s = s.replace(anchor, withStamp);
  if (crlf) s = s.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('已把时间基准移到启动前');
}
