// 两个「假通过」修复：
//   1) powershell -File script.ps1 -Targets 1.21,1.21.1 时，整个 "1.21,1.21.1" 会作为
//      单个字符串绑给 [string[]]$Targets（-File 不做 PowerShell 语法解析）→ 一个 target 都匹配不上，
//      于是每个都「跳过」。要自己按逗号拆。
//   2) 一个都没跑也打印「界面验证全部通过（0 个）」。零用例必须算失败。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const anchor = `$ErrorActionPreference = 'Stop'`;
const split = `# -File 模式下 "1.21,1.21.1" 会当成一个字符串传进来，这里自己拆开
$Targets = @($Targets | ForEach-Object { $_ -split '[,\\s]+' } | Where-Object { $_ -ne '' })

$ErrorActionPreference = 'Stop'`;
if (!raw.includes('$Targets | ForEach-Object { $_ -split')) {
  raw = raw.replace(anchor, split);
}

const zeroAnchor = `if ($failed.Count -gt 0) {`;
const zeroCheck = `if ($results.Count -eq 0) {
    Write-Host '一个 target 都没跑（全部跳过）——这不算通过。' -ForegroundColor Red
    exit 1
}
if ($failed.Count -gt 0) {`;
if (!raw.includes('一个 target 都没跑')) {
  raw = raw.replace(zeroAnchor, zeroCheck);
}

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('已修：按逗号拆 target + 零用例判失败');
