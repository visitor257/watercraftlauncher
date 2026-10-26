// 修 verify-gui.ps1 的 target 来源优先级：环境变量/参数 > 默认清单（不是并集）。
//
// 上一版把 `$env:MCVER_TARGETS` 和参数默认值（35 个版本的清单）**合并**了，
// 结果只想跑 6 个版本时，跑完还会顺着默认清单继续往下跑，撞上「fabric-api-map.json 里没有 MC 1.21」
// 直接抛错。默认清单只有在显式什么都没给的时候才该生效。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

// 1) 把参数默认值（那一大坨 target 清单）抽成 $defaultTargets
const m = raw.match(/\[string\[\]\]\$Targets = @\(\n([\s\S]*?)\n\)/);
if (!m) { console.log('MISS: 默认清单'); process.exit(1); }
const listBody = m[1];
raw = raw.replace(m[0], `[string[]]$Targets = @()`);

// 2) 在 target 归一段落里用上 defaultTargets
const oldSel = `$fromEnv = @()
if ($env:MCVER_TARGETS) { $fromEnv = $env:MCVER_TARGETS -split '[,\\s]+' }
$Targets = @(@($fromEnv) + @($Targets) |`;
const newSel = `# 默认清单：只在没给任何指定时用（**不做并集**，否则只想跑几个版本时会顺带跑完整个清单）
$defaultTargets = @(
${listBody}
)
$fromEnv = @()
if ($env:MCVER_TARGETS) { $fromEnv = $env:MCVER_TARGETS -split '[,\\s]+' }
$given = @(@($fromEnv) + @($Targets) | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
if ($given.Count -eq 0) { $given = $defaultTargets }
$Targets = @($given |`;
if (!raw.includes(oldSel)) { console.log('MISS: 选择块'); process.exit(1); }
raw = raw.replace(oldSel, newSel);

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('已修：指定 > 默认，不并集');
