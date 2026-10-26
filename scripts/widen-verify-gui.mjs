// verify-gui.ps1 改成「一个 1.21 通用 jar + 12 个 1.21.x 版本」的形态。
//
// 变化：
//   1) 默认 Target 列表 = 1.21 全系 12 个 + 5 个老 target；
//   2) Ensure-Mods 支持「版本 → 用哪个 jar」的映射（1.21.x 全部用 mc1.21.jar）；
//   3) fabric-api 表补齐 1.21.2/1.21.3/1.21.5~1.21.8；
//   4) Disable-Onboarding 在 options.txt 不存在时**创建**它（新装的实例没有这个文件，
//      而 1.21 起的新版本首次启动都会弹引导屏，不关掉就永远等不到 TitleScreen）。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

// ---- 1) 默认 target 列表
raw = raw.replace(
  /\[string\[\]\]\$Targets = @\('[^']*'[^\)]*\)/,
  `[string[]]$Targets = @('1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11', '1.20.1', '1.19.4', '1.18.2', '1.17.1', '1.16.5')`
);

// ---- 2) 实例覆盖表：1.21 系列
raw = raw.replace(
  /(\$instanceOverride = @\{\n)/,
  `$1    # 1.21 ~ 1.21.11 全部用 dist\\...-mc1.21.jar 这一个 jar（见 Ensure-Mods 的 jarForVersion）
    '1.21' = 'w121'
`
);

// ---- 3) fabric-api 表：补齐 1.21 全系
raw = raw.replace(
  /(\$fabricApi = @\{\n)/,
  `$1    '1.21.8'  = @{ v = '0.136.1+1.21.8';  url = '' }
    '1.21.7'  = @{ v = '0.129.0+1.21.7';  url = '' }
    '1.21.6'  = @{ v = '0.128.2+1.21.6';  url = '' }
    '1.21.5'  = @{ v = '0.128.2+1.21.5';  url = '' }
    '1.21.3'  = @{ v = '0.114.1+1.21.3';  url = '' }
    '1.21.2'  = @{ v = '0.106.1+1.21.2';  url = '' }
    '1.21'    = @{ v = '0.102.0+1.21';    url = '' }
`
);

// ---- 4) options.txt 不存在就创建
raw = raw.replace(
  `    $opt = Join-Path $testgame "versions\\$id\\options.txt"
    if (-not (Test-Path $opt)) { return }`,
  `    $opt = Join-Path $testgame "versions\\$id\\options.txt"
    if (-not (Test-Path $opt)) {
        # 新装的实例没有 options.txt；直接写一行关掉引导屏，游戏会自己补默认值
        [System.IO.File]::WriteAllLines($opt, @('onboardAccessibility:false'), (New-Object System.Text.UTF8Encoding($false)))
        return
    }`
);

// ---- 5) jar 选择：1.21.x 一律用 mc1.21.jar
raw = raw.replace(
  `    $jar = Join-Path $root "dist\\watercraftlauncher-fabric-1.0.0-mc$v.jar"
    if (-not (Test-Path $jar)) { throw "没有构建产物 $jar" }`,
  `    # 1.21 ~ 1.21.11 共用一个 jar（mc1.21.jar），其余 target 一个版本一个 jar
    $jarVer = $v
    if ($v -like '1.21*') { $jarVer = '1.21' }
    $jar = Join-Path $root "dist\\watercraftlauncher-fabric-1.0.0-mc$jarVer.jar"
    if (-not (Test-Path $jar)) { throw "没有构建产物 $jar" }`
);

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('verify-gui.ps1 已改造');
