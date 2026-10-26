// Ensure-Mods 拷新 jar 之后，把 mods 里**其它版本的** watercraftlauncher jar 删掉。
// 不删的后果：1.21.9/1.21.10/1.21.11 实例里还留着旧的 mc1.21.9.jar / mc1.21.10.jar，
// 和新拷进去的 mc1.21.jar 同时存在 → 同一个 mod id 出现两份，loader 报重复 / 只加载其中一个，
// 验证结果就说不清是哪个 jar 的表现。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const anchor = `    Copy-Item $jar $mods -Force`;
const add = `    Copy-Item $jar $mods -Force
    # 清掉同 mod 的旧 jar，免得同一个 mod id 有两份
    Get-ChildItem $mods -Filter 'watercraftlauncher-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne (Split-Path -Leaf $jar) } |
        ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "  清掉旧 jar $($_.Name)" }`;

if (raw.includes('清掉同 mod 的旧 jar')) {
  console.log('已经改过');
} else {
  if (!raw.includes(anchor)) { console.log('MISS'); process.exit(1); }
  raw = raw.replace(anchor, add);
  if (crlf) raw = raw.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
  console.log('Ensure-Mods 现在会清掉旧 jar');
}
