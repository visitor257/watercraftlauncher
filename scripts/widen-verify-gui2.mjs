// verify-gui.ps1 → 「一个 1.xx 系列一个 jar」版本。
//   1) fabric-api 表改成读 logs/build/fabric-api-map.json（23 个补丁版本，逐版本查好的）；
//   2) jar 选择按系列取（1.20.6 → mc1.20.jar）；
//   3) 实例目录自动找：V<v>-FABRIC 或 fabric-loader-0.20.1-<v>；
//   4) 默认 target = 1.21 全系 + 1.20/1.19/1.18/1.17/1.16 全系，共 35 个。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;
const before = raw;

// ---- 1) targets 默认值
raw = raw.replace(
  /\[string\[\]\]\$Targets = @\('[^\)]*\)/,
  `[string[]]$Targets = @(
    '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
    '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
    '1.19', '1.19.1', '1.19.2', '1.19.3', '1.19.4',
    '1.18', '1.18.1', '1.18.2',
    '1.17', '1.17.1',
    '1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5'
)`
);

// ---- 2) fabric-api 表 → JSON
const apiStart = raw.indexOf('# Fabric API 版本（下载到');
const apiEnd = raw.indexOf('# 首次启动的无障碍引导屏');
if (apiStart < 0 || apiEnd < 0) { console.log('MISS: fabric-api 块'); process.exit(1); }
const apiBlock = `# Fabric API：每个补丁版本用哪个由 scripts\\fetch-fabric-api-all.mjs 现查好写进 JSON
# （1.16 和 1.16.5 能用的 fabric-api 就不是同一份，不能靠猜）。
$apiMap = @{}
$mapFile = Join-Path $logs 'fabric-api-map.json'
if (-not (Test-Path $mapFile)) { throw "缺少 $mapFile —— 先跑 node scripts\\fetch-fabric-api-all.mjs" }
(Get-Content $mapFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object {
    if ($_.Value) { $apiMap[$_.Name] = $_.Value }
}

`;
raw = raw.slice(0, apiStart) + apiBlock + raw.slice(apiEnd);

// ---- 3) Ensure-Mods：按系列选 jar + 用 JSON 里的 fabric-api
const emStart = raw.indexOf('function Ensure-Mods([string]$id, [string]$v) {');
const emEnd = raw.indexOf('$results = @()');
if (emStart < 0 || emEnd < 0) { console.log('MISS: Ensure-Mods'); process.exit(1); }
const emBlock = `function Ensure-Mods([string]$id, [string]$v) {
    $mods = Join-Path $testgame "versions\\$id\\mods"
    New-Item -ItemType Directory -Force -Path $mods | Out-Null

    # 一个 jar 盖一整个 1.xx 系列：1.20.6 → dist\\...-mc1.20.jar
    $series = $v -replace '^(1\\.\\d+).*$', '$1'
    $jar = Join-Path $root "dist\\watercraftlauncher-fabric-1.0.0-mc$series.jar"
    if (-not (Test-Path $jar)) { throw "没有构建产物 $jar" }
    Copy-Item $jar $mods -Force
    # 清掉同 mod 的旧 jar，免得同一个 mod id 有两份
    Get-ChildItem $mods -Filter 'watercraftlauncher-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne (Split-Path -Leaf $jar) } |
        ForEach-Object { Remove-Item $_.FullName -Force; Write-Host "  清掉旧 jar $($_.Name)" }

    # fabric-api 也按版本换：换 MC 版本时必须换掉旧的，否则 loader 直接拒载
    $api = $apiMap[$v]
    if (-not $api) { throw "fabric-api-map.json 里没有 MC $v" }
    $cache = Join-Path $testgame '.cache'
    $src = Join-Path $cache $api.file
    if (-not (Test-Path $src)) { throw "缺少 fabric-api 缓存文件 $src" }
    Get-ChildItem $mods -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -ne $api.file } |
        ForEach-Object { Remove-Item $_.FullName -Force }
    Copy-Item $src $mods -Force
}

`;
raw = raw.slice(0, emStart) + emBlock + raw.slice(emEnd);

// ---- 4) 实例目录：自动找
raw = raw.replace(
  `    $id = "V$v-FABRIC"
    if ($instanceOverride.ContainsKey($v)) { $id = $instanceOverride[$v] }`,
  `    $id = $null
    if ($instanceOverride.ContainsKey($v)) { $id = $instanceOverride[$v] }
    if (-not $id) {
        foreach ($cand in @("V$v-FABRIC", "fabric-loader-0.20.1-$v")) {
            if (Test-Path (Join-Path $testgame "versions\\$cand")) { $id = $cand; break }
        }
    }
    if (-not $id) { $id = "V$v-FABRIC" }`
);

if (raw === before) { console.log('没有任何改动 —— 检查锚点'); process.exit(1); }
if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('verify-gui.ps1 已支持系列级 jar + 逐版本 fabric-api');
