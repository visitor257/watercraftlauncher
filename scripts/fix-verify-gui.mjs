// 修 verify-gui.ps1 的两个真问题（都会导致「假通过」）：
//
// 1) PowerShell 5.1 里 `($results | Where-Object {...}).Count` 在**只命中一个对象**时返回 $null
//    （PSCustomObject 没有 Count 属性）→ `-gt 0` 为假 → 明明 FAIL 却打印「界面验证全部通过。」。
//    必须用 @(...) 强制数组化。
// 2) 等标记的循环会把「上一轮遗留的旧日志」当成这一轮的：
//    改成只认启动之后新建的日志、且要求日志行号增加，避免拿旧结果截黑图。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
if (crlf) s = s.replace(/\r\n/g, '\n');
const before = s;

// --- 1) 汇总判断
s = s.replace(
  `if (($results | Where-Object { -not $_.Ok }).Count -gt 0) { exit 1 }
Write-Host '界面验证全部通过。' -ForegroundColor Green`,
  `# 注意：必须用 @() 包起来。单个对象时 .Count 是 $null，-gt 0 为假 → 会变成假通过（踩过）。
$failed = @($results | Where-Object { -not $_.Ok })
if ($failed.Count -gt 0) {
    Write-Host ("界面验证失败：" + (($failed | ForEach-Object { $_.Version }) -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host ("界面验证全部通过（$($results.Count) 个）。") -ForegroundColor Green`
);

// --- 2) 只看本轮新产生的日志
s = s.replace(
  `    $gameLog = $null
    for ($i = 0; $i -lt 90; $i++) {
        $gameLog = Get-ChildItem (Join-Path $testgame "versions\\$id\\logs") -Filter 'mcver-launch-*.log' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($gameLog) {
            $hit = Select-String -Path $gameLog.FullName -Pattern 'screen=MAIN chrome' -ErrorAction SilentlyContinue
            if ($hit) { break }
        }
        Start-Sleep -Seconds 1
    }`,
  `    # 只认「本次启动之后」的日志：否则会拿上一轮遗留的日志当命中，提前 4 秒去截一张黑图。
    $launchedAt = Get-Date
    $gameLog = $null
    for ($i = 0; $i -lt 120; $i++) {
        $gameLog = Get-ChildItem (Join-Path $testgame "versions\\$id\\logs") -Filter 'mcver-launch-*.log' -ErrorAction SilentlyContinue |
            Where-Object { $_.CreationTime -ge $launchedAt.AddSeconds(-5) } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($gameLog) {
            $hit = Select-String -Path $gameLog.FullName -Pattern 'screen=MAIN chrome' -ErrorAction SilentlyContinue
            if ($hit) { break }
        }
        Start-Sleep -Seconds 1
    }
    if (-not $gameLog) { Write-Host "[$v] 警告：没找到本轮日志，可能是启动失败" -ForegroundColor Yellow }`
);

if (s === before) {
  console.log('没有任何改动 —— 说明原文对不上，检查一下');
  process.exit(1);
}
if (crlf) s = s.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(s, 'utf8'));
console.log('verify-gui.ps1 已修');
