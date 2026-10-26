// verify-gui.ps1 再加一件事：启动前把 options.txt 的 onboardAccessibility 设成 false。
//
// 起因：1.21.10 的界面验证一直 FAIL，日志里 currentScreen 是
//   net.minecraft.class_8032 → yarn 映射到 AccessibilityOnboardingScreen
// （1.21.9 才开始有的首次启动「无障碍引导」屏）。
// 自检钩子只认 TitleScreen，所以引导屏挡在前面时永远等不到界面。
// 这不是产品 bug（真人会点掉引导），但验证必须先把这一屏关掉，否则结果不可复现。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
if (crlf) s = s.replace(/\r\n/g, '\n');

const anchor = `function Ensure-Mods([string]$id, [string]$v) {`;
const fn = `# 首次启动的无障碍引导屏（1.21.9+ 的 AccessibilityOnboardingScreen）会挡在 TitleScreen 前面，
# 自检钩子等不到界面 → 先把它关掉。注意：只改这一行，其它选项原样保留。
function Disable-Onboarding([string]$id) {
    $opt = Join-Path $testgame "versions\\$id\\options.txt"
    if (-not (Test-Path $opt)) { return }
    $lines = Get-Content $opt
    $found = $false
    $out = foreach ($l in $lines) {
        if ($l -like 'onboardAccessibility:*') { $found = $true; 'onboardAccessibility:false' } else { $l }
    }
    if (-not $found) { $out = @($out) + 'onboardAccessibility:false' }
    [System.IO.File]::WriteAllLines($opt, $out, (New-Object System.Text.UTF8Encoding($false)))
}

function Ensure-Mods([string]$id, [string]$v) {`;

const callAnchor = `    Ensure-Mods $id $v`;
const callNew = `    Ensure-Mods $id $v
    Disable-Onboarding $id`;

if (s.includes('Disable-Onboarding')) {
  console.log('已经加过了');
} else {
  if (!s.includes(anchor) || !s.includes(callAnchor)) {
    console.log('MISS: 结构变了');
    process.exit(1);
  }
  s = s.replace(anchor, fn).replace(callAnchor, callNew);
  if (crlf) s = s.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('已加 Disable-Onboarding');
}
