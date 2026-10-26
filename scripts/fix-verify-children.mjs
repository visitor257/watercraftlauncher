// children= 一直抓不到（显示 '-'）的原因：解析发生在游戏进程还活着的时候，
// 日志文件被游戏以写模式独占，Select-String 读不到（-ErrorAction SilentlyContinue 把异常吞了）。
// 顺序改成：先抓图 → 杀掉游戏 → 再读日志取 children。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
if (crlf) s = s.replace(/\r\n/g, '\n');

const oldBlock = `    # children= 是控件个数，用来间接确认按钮确实建出来了（Btn 适配层）
    $children = '-'
    if ($gameLog) {
        $m = Select-String -Path $gameLog.FullName -Pattern 'children=(\\d+)' | Select-Object -Last 1
        if ($m) { $children = $m.Matches[0].Groups[1].Value }
    }

    $ok = ($center -eq 'C6C6C6' -and $hits -gt 10000)`;

const newBlock = `    # 先杀进程再读日志：游戏活着时日志是独占写，Select-String 会读不到内容（静默返回空）。
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($proc -and -not $proc.HasExited) { $proc.Kill() }
    Start-Sleep -Seconds 2

    # children= 是控件个数，用来间接确认按钮确实建出来了（Btn 适配层）
    $children = '-'
    if ($gameLog) {
        $m = Select-String -Path $gameLog.FullName -Pattern 'children=(\\d+)' -ErrorAction SilentlyContinue | Select-Object -Last 1
        if ($m) { $children = $m.Matches[0].Groups[1].Value }
    }

    $ok = ($center -eq 'C6C6C6' -and $hits -gt 10000 -and $children -ne '-')`;

const oldTail = `    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($proc -and -not $proc.HasExited) { $proc.Kill() }
    Start-Sleep -Seconds 2
}`;

const newTail = `}`;

if (s.includes('先杀进程再读日志')) {
  console.log('已经改过');
} else {
  if (!s.includes(oldBlock) || !s.includes(oldTail)) {
    console.log('MISS: 结构变了');
    process.exit(1);
  }
  s = s.replace(oldBlock, newBlock).replace(oldTail, newTail);
  if (crlf) s = s.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('已调整读取顺序（children 现在也参与判定）');
}
