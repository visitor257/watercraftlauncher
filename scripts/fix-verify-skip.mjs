// 修两个让「跳过」变成「通过」的问题。
//
// 1) `powershell -File script.ps1 -Targets 1.20,1.20.1` 里的 `1.20` 会被 PowerShell
//    当**数字**解析（double 1.2），再转回 string 变成 `1.2` —— 于是查不到实例、整条被跳过。
//    实测：`-Targets 1.20,1.21.11,1.16` → 脚本里收到的是 `1.2,1.21.11,1.16`。
//    `1.16` / `1.21.11` 恰好幸免，所以这个坑一直被掩盖着。
//    修法：target 列表走环境变量 `MCVER_TARGETS`（纯字符串，不经参数绑定），
//    同时 `-Targets` 仍然能用（只要别传结尾是 0 的版本号）。两条路都走一遍「归一化」。
// 2) 有 target 被跳过时仍然打印「界面验证全部通过（N 个）」。跳过 = 没有证据，
//    必须算失败；确需容忍时显式加 `-AllowSkip`。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

// ---- 1) 参数加 -AllowSkip
if (!raw.includes('[switch]$AllowSkip')) {
  raw = raw.replace(
    `    [string]$JavaHome21 = 'D:\\Program Files\\Java\\jdk-21.0.7'`,
    `    [string]$JavaHome21 = 'D:\\Program Files\\Java\\jdk-21.0.7',
    # 跳过（实例不存在）默认算失败；确实只跑一部分时显式打开
    [switch]$AllowSkip`
  );
}

// ---- 2) target 来源：环境变量优先
const oldSplit = `# -File 模式下 "1.21,1.21.1" 会当成一个字符串传进来，这里自己拆开
$Targets = @($Targets | ForEach-Object { $_ -split '[,\\s]+' } | Where-Object { $_ -ne '' })`;
const newSplit = `# 两个来源合并：
#   - 环境变量 MCVER_TARGETS（推荐）。-File 模式下 1.20 会被 PowerShell 当数字变成 1.2，
#     走环境变量就完全不经过参数绑定，不会有这个问题。
#   - 显式 -Targets（注意别传结尾是 0 的版本号）。
$fromEnv = @()
if ($env:MCVER_TARGETS) { $fromEnv = $env:MCVER_TARGETS -split '[,\\s]+' }
$Targets = @(@($fromEnv) + @($Targets) |
    ForEach-Object { "$_".Trim() } |
    Where-Object { $_ -ne '' } |
    Select-Object -Unique)
if ($Targets.Count -eq 0) { throw '没有指定任何 target（用 MCVER_TARGETS 环境变量或 -Targets）' }`;

if (raw.includes(oldSplit)) {
  raw = raw.replace(oldSplit, newSplit);
} else if (!raw.includes('MCVER_TARGETS')) {
  console.log('MISS: target 拆分块');
  process.exit(1);
}

// ---- 3) 跳过要计数
if (!raw.includes('$skipped = @()')) {
  raw = raw.replace(`$results = @()`, `$results = @()\n$skipped = @()`);
  raw = raw.replace(
    `        Write-Host "[$v] 跳过：没有 $id" -ForegroundColor Yellow
        continue`,
    `        Write-Host "[$v] 跳过：没有 $id" -ForegroundColor Yellow
        $skipped += $v
        continue`
  );
}

// ---- 4) 结尾判定：跳过也算失败
const tailOld = `if ($results.Count -eq 0) {
    Write-Host '一个 target 都没跑（全部跳过）——这不算通过。' -ForegroundColor Red
    exit 1
}`;
const tailNew = `if ($results.Count -eq 0) {
    Write-Host '一个 target 都没跑（全部跳过）——这不算通过。' -ForegroundColor Red
    exit 1
}
if ($skipped.Count -gt 0 -and -not $AllowSkip) {
    Write-Host "有 $($skipped.Count) 个 target 被跳过（实例不存在）：$($skipped -join ', ')" -ForegroundColor Red
    Write-Host '跳过 = 没有证据，不算通过。确实只想跑一部分就加 -AllowSkip。' -ForegroundColor Red
    exit 1
}`;
if (raw.includes(tailOld)) { raw = raw.replace(tailOld, tailNew); } else { console.log('WARN: 结尾判定块没匹配上'); }

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('已修：MCVER_TARGETS 环境变量 + 跳过算失败');
