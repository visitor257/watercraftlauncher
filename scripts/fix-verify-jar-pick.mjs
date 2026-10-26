// verify-gui.ps1：jar 按系列取，但要能识别「系列内部又拆了一个 jar」的情况。
//   mc1.19.jar   → 1.19 / 1.19.1 / 1.19.2
//   mc1.19.3.jar → 1.19.3 / 1.19.4
//   mc1.20.jar   → 1.20 ~ 1.20.6
//   mc1.21.jar   → 1.21 ~ 1.21.11
// 规则：从「最具体」的 jar 名往下试，先试 <完整版本号>，再试 1.XX.Y，最后 1.XX。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const oldBlock = `    # 一个 jar 盖一整个 1.xx 系列：1.20.6 → dist\\...-mc1.20.jar
    $series = $v -replace '^(1\\.\\d+).*$', '$1'
    $jar = Join-Path $root "dist\\watercraftlauncher-fabric-1.0.0-mc$series.jar"
    if (-not (Test-Path $jar)) { throw "没有构建产物 $jar" }`;
const newBlock = `    # 选 jar：先试最具体的名字，再往回退。
    #   1.19.3 → 先找 mc1.19.3.jar（1.19 系列内部拆出来的），没有再退到 mc1.19.jar
    #   1.20.6 → mc1.20.6.jar 不存在 → mc1.20.6？→ mc1.20.jar
    $cands = @()
    $parts = $v -split '\\.'
    if ($parts.Count -ge 2) { $cands += ($parts[0] + '.' + $parts[1]) }              # 1.19
    if ($parts.Count -ge 3) { $cands += ($parts[0] + '.' + $parts[1] + '.' + $parts[2]) }  # 1.19.3
    if ($parts.Count -ge 2) { $cands += ($parts[0] + '.' + $parts[1]) }              # 兜底
    $jar = $null
    # 从最长的开始试（1.19.3 优先于 1.19）
    foreach ($c in ($cands | Sort-Object { $_.Length } -Descending -Unique)) {
        $try = Join-Path $root "dist\\watercraftlauncher-fabric-1.0.0-mc$c.jar"
        if (Test-Path $try) { $jar = $try; break }
    }
    if (-not $jar) { throw "没有找到 MC $v 对应的 jar（试过：$($cands -join ', ')）" }`;

if (!raw.includes(oldBlock)) { console.log('MISS: jar 选择块'); process.exit(1); }
raw = raw.replace(oldBlock, newBlock);

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('verify-gui.ps1 的 jar 选择已支持系列内拆分');
