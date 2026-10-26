// verify-gui.ps1 的 jar 选择：改成**读每个 jar 的 fabric.mod.json 里的 minecraft 依赖区间**来匹配。
//
// 之前按文件名长度猜（先试 1.19.3 再试 1.19），结果 1.19.4 猜到了 mc1.19.jar（区间是
// >=1.19 <1.19.3）→ loader 报 HARD_DEP_INCOMPATIBLE_PRESELECTED，白白浪费一轮验证。
// 版本区间是产物里写死的真相，直接读它，不再猜。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const helper = `
# ---- 按 fabric.mod.json 里的版本区间挑 jar --------------------------------------
# 每个 dist 里的 jar 都在 fabric.mod.json 写了 "minecraft": ">=A <B"，
# 这就是它到底能盖哪些版本的**唯一真相**。按文件名猜会出错（1.19.4 会猜到 mc1.19）。
function ConvertTo-VerTuple([string]$v) {
    $parts = $v.Trim().Split('.')
    $nums = @()
    foreach ($x in $parts) {
        $n = 0
        if ([int]::TryParse($x, [ref]$n)) { $nums += $n } else { $nums += 0 }
    }
    while ($nums.Count -lt 3) { $nums += 0 }
    return ,$nums
}

function Compare-Ver([string]$a, [string]$b) {
    $x = ConvertTo-VerTuple $a
    $y = ConvertTo-VerTuple $b
    for ($i = 0; $i -lt 3; $i++) {
        if ($x[$i] -ne $y[$i]) { return [math]::Sign($x[$i] - $y[$i]) }
    }
    return 0
}

function Test-InRange([string]$v, [string]$range) {
    # 只处理 ">=A" / ">=A <B" 两种（build.gradle 只会生成这两种）
    if ($range -match '>=\\s*([0-9.]+)') {
        if ((Compare-Ver $v $Matches[1]) -lt 0) { return $false }
    }
    if ($range -match '<\\s*([0-9.]+)') {
        if ((Compare-Ver $v $Matches[1]) -ge 0) { return $false }
    }
    return $true
}

$script:jarRangeCache = $null
function Get-JarRanges {
    if ($script:jarRangeCache) { return $script:jarRangeCache }
    Add-Type -AssemblyName System.IO.Compression.FileSystem | Out-Null
    $map = @()
    foreach ($j in (Get-ChildItem (Join-Path $root 'dist') -Filter 'watercraftlauncher-*.jar' -ErrorAction SilentlyContinue)) {
        try {
            $z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
            $e = $z.Entries | Where-Object { $_.FullName -eq 'fabric.mod.json' } | Select-Object -First 1
            if (-not $e) { $z.Dispose(); continue }
            $r = New-Object System.IO.StreamReader($e.Open())
            $txt = $r.ReadToEnd()
            $r.Close()
            $z.Dispose()
            $dep = ($txt | ConvertFrom-Json).depends.minecraft
            if ($dep) { $map += [pscustomobject]@{ Path = $j.FullName; Range = $dep } }
        } catch {
            Write-Host "  [warn] 读不了 $($j.Name)：$_" -ForegroundColor DarkYellow
        }
    }
    $script:jarRangeCache = $map
    return $map
}

function Find-JarFor([string]$v) {
    foreach ($m in (Get-JarRanges)) {
        if (Test-InRange $v $m.Range) { return $m.Path }
    }
    throw "没有哪个 jar 的版本区间包含 MC $v（现有：$((Get-JarRanges | ForEach-Object { (Split-Path $_.Path -Leaf) + '=' + $_.Range }) -join '; '))"
}
# -----------------------------------------------------------------------------
`;

// 把 helper 插在 "$results = @()" 之前
if (!raw.includes('function Find-JarFor')) {
  const anchor = '$results = @()';
  if (!raw.includes(anchor)) { console.log('MISS: results 锚点'); process.exit(1); }
  raw = raw.replace(anchor, helper + '\n' + anchor);
}

// 替换 jar 选择块
const oldSel = /    # 选 jar：先试最具体的名字[\s\S]*?if \(-not \$jar\) \{ throw "没有找到 MC \$v 对应的 jar（试过：\$\(\$cands -join ', '\)）" \}/;
if (!oldSel.test(raw)) { console.log('MISS: jar 选择块'); process.exit(1); }
raw = raw.replace(oldSel, `    # 按产物自己声明的版本区间挑（1.19.4 → mc1.19.3.jar，1.19.2 → mc1.19.jar）
    $jar = Find-JarFor $v`);

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('verify-gui.ps1 现在按 fabric.mod.json 的版本区间选 jar');
