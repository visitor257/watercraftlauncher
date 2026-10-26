# 界面级验证：真实窗口抓图 + 数面板特征像素
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\verify-gui.ps1 [-Targets 1.19.4,1.20.1]
#
# 为什么要这么麻烦（2026-10-26 的教训）：
#   - 日志里出现 screen=MAIN 只证明 init() 跑过，不证明画出来了；
#   - 无头环境下 ScreenshotRecorder 早期抓到的是启动画面，会骗人；
#   - 所以这里起真进程 → 等够首帧 → PrintWindow 抓真窗口 → 数 #C6C6C6（面板色）。
#
# 注意：1.16.5 这个实例的 version json 里 javaVersion 被改成了 17。
# 原版 1.16.5 声明要 Java 8，但本 mod 是 Java 17 字节码（见 PROGRESS §13 的取舍），
# 而且在 Java 8 上 loader 0.20.1 也跑得起 —— 所以不手动钉 17 就永远看不到界面。

param(
    [string[]]$Targets = @(),
    [int]$WarmupSec = 40,
    [string]$JavaHome21 = 'D:\Program Files\Java\jdk-21.0.7',
    # 跳过（实例不存在）默认算失败；确实只跑一部分时显式打开
    [switch]$AllowSkip
)

# 两个来源合并：
#   - 环境变量 MCVER_TARGETS（推荐）。-File 模式下 1.20 会被 PowerShell 当数字变成 1.2，
#     走环境变量就完全不经过参数绑定，不会有这个问题。
#   - 显式 -Targets（注意别传结尾是 0 的版本号）。
# 默认清单：只在没给任何指定时用（**不做并集**，否则只想跑几个版本时会顺带跑完整个清单）
$defaultTargets = @(
    '1.21', '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11',
    '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
    '1.19', '1.19.1', '1.19.2', '1.19.3', '1.19.4',
    '1.18', '1.18.1', '1.18.2',
    '1.17', '1.17.1',
    '1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4', '1.16.5'
)
$fromEnv = @()
if ($env:MCVER_TARGETS) { $fromEnv = $env:MCVER_TARGETS -split '[,\s]+' }
$given = @(@($fromEnv) + @($Targets) | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
if ($given.Count -eq 0) { $given = $defaultTargets }
$Targets = @($given |
    ForEach-Object { "$_".Trim() } |
    Where-Object { $_ -ne '' } |
    Select-Object -Unique)
if ($Targets.Count -eq 0) { throw '没有指定任何 target（用 MCVER_TARGETS 环境变量或 -Targets）' }

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$testgame = Join-Path $root 'testgame'
$logs = Join-Path $root 'logs\build'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

Add-Type -AssemblyName System.Drawing
if (-not ('Shot' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class Shot {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
    public struct R { public int L, T, Ri, B; }
}
'@
}

function Get-GameWindow {
    Get-Process java -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' } |
        Select-Object -First 1
}

# 实例目录名默认是 V<mc>-FABRIC；1.17.1 是用我们自己安装器装的，目录名是 loader 版的
$instanceOverride = @{
    # 1.21 ~ 1.21.11 全部用 dist\...-mc1.21.jar 这一个 jar（见 Ensure-Mods 里按版本选 jar）
    '1.21' = 'w121'
    '1.21.2' = 'fabric-loader-0.20.1-1.21.2'
    '1.21.3' = 'fabric-loader-0.20.1-1.21.3'
    '1.21.6' = 'fabric-loader-0.20.1-1.21.6'
    '1.21.7' = 'fabric-loader-0.20.1-1.21.7'
    '1.17.1' = 'fabric-loader-0.20.1-1.17.1'
    '1.21.11' = 'v12111'
    '1.21.10' = 'v12110'
    '1.21.9' = 'v1219'
}

# Fabric API：每个补丁版本用哪个由 scripts\fetch-fabric-api-all.mjs 现查好写进 JSON
# （1.16 和 1.16.5 能用的 fabric-api 就不是同一份，不能靠猜）。
$apiMap = @{}
$mapFile = Join-Path $logs 'fabric-api-map.json'
if (-not (Test-Path $mapFile)) { throw "缺少 $mapFile —— 先跑 node scripts\fetch-fabric-api-all.mjs" }
(Get-Content $mapFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object {
    if ($_.Value) { $apiMap[$_.Name] = $_.Value }
}

# 首次启动的无障碍引导屏（1.21.9+ 的 AccessibilityOnboardingScreen）会挡在 TitleScreen 前面，
# 自检钩子等不到界面 → 先把它关掉。注意：只改这一行，其它选项原样保留。
function Disable-Onboarding([string]$id) {
    $opt = Join-Path $testgame "versions\$id\options.txt"
    if (-not (Test-Path $opt)) {
        # 新装的实例没有 options.txt；直接写一行关掉引导屏，游戏会自己补默认值
        [System.IO.File]::WriteAllLines($opt, @('onboardAccessibility:false'), (New-Object System.Text.UTF8Encoding($false)))
        return
    }
    $lines = Get-Content $opt
    $found = $false
    $out = foreach ($l in $lines) {
        if ($l -like 'onboardAccessibility:*') { $found = $true; 'onboardAccessibility:false' } else { $l }
    }
    if (-not $found) { $out = @($out) + 'onboardAccessibility:false' }
    [System.IO.File]::WriteAllLines($opt, $out, (New-Object System.Text.UTF8Encoding($false)))
}

function Ensure-Mods([string]$id, [string]$v) {
    $mods = Join-Path $testgame "versions\$id\mods"
    New-Item -ItemType Directory -Force -Path $mods | Out-Null

    # 按产物自己声明的版本区间挑（1.19.4 → mc1.19.3.jar，1.19.2 → mc1.19.jar）
    $jar = Find-JarFor $v
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
    if ($range -match '>=\s*([0-9.]+)') {
        if ((Compare-Ver $v $Matches[1]) -lt 0) { return $false }
    }
    if ($range -match '<\s*([0-9.]+)') {
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

$results = @()
$skipped = @()
foreach ($v in $Targets) {
    $id = $null
    if ($instanceOverride.ContainsKey($v)) { $id = $instanceOverride[$v] }
    if (-not $id) {
        foreach ($cand in @("V$v-FABRIC", "fabric-loader-0.20.1-$v")) {
            if (Test-Path (Join-Path $testgame "versions\$cand")) { $id = $cand; break }
        }
    }
    if (-not $id) { $id = "V$v-FABRIC" }
    if (-not (Test-Path (Join-Path $testgame "versions\$id"))) {
        Write-Host "[$v] 跳过：没有 $id" -ForegroundColor Yellow
        $skipped += $v
        continue
    }

    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2

    # 实例必须开着版本隔离，否则日志/options.txt 都不在这个目录里
    $marker = Join-Path $testgame "versions\$id\.mcver-isolated"
    if (-not (Test-Path $marker)) { New-Item -ItemType File -Force -Path $marker | Out-Null }

    Ensure-Mods $id $v
    Disable-Onboarding $id

    # 时间基准取在启动之前：日志在启动后 2~5 秒就创建，窗口要 15~20 秒才出现。
    $launchedAt = Get-Date
    $logFile = Join-Path $logs "hold-$v.log"
    $env:MCVER_OPEN_GUI = '1'
    $args = @('-cp', (Join-Path $root 'tools\probe\out'), 'dev.mcver.probe.ProbeLaunch', $testgame, $id, '200', 'ZZNEVERZZ')
    $proc = Start-Process -FilePath (Join-Path $JavaHome21 'bin\java.exe') -ArgumentList $args `
        -WorkingDirectory $root -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err" -PassThru

    $win = $null
    for ($i = 0; $i -lt $WarmupSec; $i++) {
        Start-Sleep -Seconds 1
        $win = Get-GameWindow
        if ($win) { break }
    }

    # 只看窗口出现不够：首帧可能还是启动画面（atlas / 全景背景要 3~10 秒）。
    # 等游戏日志里出现面板绘制标记（"screen=MAIN chrome" 是 ASCII 前缀，中文会被写成乱码）。
    # 只认「本次启动之后」的日志：否则会拿上一轮遗留的日志当命中，提前 4 秒去截一张黑图。
    $gameLog = $null
    for ($i = 0; $i -lt 120; $i++) {
        $gameLog = Get-ChildItem (Join-Path $testgame "versions\$id\logs") -Filter 'mcver-launch-*.log' -ErrorAction SilentlyContinue |
            Where-Object { $_.CreationTime -ge $launchedAt.AddSeconds(-5) } |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($gameLog) {
            $hit = Select-String -Path $gameLog.FullName -Pattern 'screen=MAIN chrome' -ErrorAction SilentlyContinue
            if ($hit) { break }
        }
        Start-Sleep -Seconds 1
    }
    if (-not $gameLog) { Write-Host "[$v] 警告：没找到本轮日志，可能是启动失败" -ForegroundColor Yellow }
    Start-Sleep -Seconds 4
    $win = Get-GameWindow

    if (-not $win) {
        Write-Host "[$v] 失败：没有窗口" -ForegroundColor Red
        $results += [pscustomobject]@{ Version = $v; Center = '-'; Hits = 0; Children = '-'; Ok = $false }
        Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
        continue
    }

    $r = New-Object Shot+R
    [void][Shot]::GetWindowRect($win.MainWindowHandle, [ref]$r)
    $w = $r.Ri - $r.L
    $h = $r.B - $r.T
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $dc = $g.GetHdc()
    [void][Shot]::PrintWindow($win.MainWindowHandle, $dc, 2)
    $g.ReleaseHdc($dc)
    $g.Dispose()

    $png = Join-Path $logs "gui-$v.png"
    $bmp.Save($png, [System.Drawing.Imaging.ImageFormat]::Png)

    $hits = 0
    for ($y = 0; $y -lt $h; $y += 4) {
        for ($x = 0; $x -lt $w; $x += 4) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.R -eq 198 -and $c.G -eq 198 -and $c.B -eq 198) { $hits++ }
        }
    }
    $c0 = $bmp.GetPixel([int]($w / 2), [int]($h / 2))
    $center = '{0:X2}{1:X2}{2:X2}' -f $c0.R, $c0.G, $c0.B
    $bmp.Dispose()

    # 先杀进程再读日志：游戏活着时日志是独占写，Select-String 会读不到内容（静默返回空）。
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($proc -and -not $proc.HasExited) { $proc.Kill() }
    Start-Sleep -Seconds 2

    # children= 是控件个数，用来间接确认按钮确实建出来了（Btn 适配层）
    $children = '-'
    if ($gameLog) {
        Write-Host "  [debug] log=$($gameLog.FullName) 存在=$(Test-Path $gameLog.FullName)" -ForegroundColor DarkGray
        $m = Select-String -Path $gameLog.FullName -Pattern 'children=(\d+)' -ErrorAction SilentlyContinue | Select-Object -Last 1
        Write-Host "  [debug] children 命中=$(if ($m) { 1 } else { 0 })" -ForegroundColor DarkGray
        if ($m) { $children = $m.Matches[0].Groups[1].Value }
    }

    $ok = ($center -eq 'C6C6C6' -and $hits -gt 10000 -and $children -ne '-')
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[$v] center=$center hits=$hits children=$children -> " + $(if ($ok) { 'OK' } else { 'FAIL' })) -ForegroundColor $color
    $results += [pscustomobject]@{ Version = $v; Center = $center; Hits = $hits; Children = $children; Ok = $ok }

}

Write-Host ''
$results | Format-Table -AutoSize
# 注意：必须用 @() 包起来。单个对象时 .Count 是 $null，-gt 0 为假 → 会变成假通过（踩过）。
$failed = @($results | Where-Object { -not $_.Ok })
if ($results.Count -eq 0) {
    Write-Host '一个 target 都没跑（全部跳过）——这不算通过。' -ForegroundColor Red
    exit 1
}
if ($skipped.Count -gt 0 -and -not $AllowSkip) {
    Write-Host "有 $($skipped.Count) 个 target 被跳过（实例不存在）：$($skipped -join ', ')" -ForegroundColor Red
    Write-Host '跳过 = 没有证据，不算通过。确实只想跑一部分就加 -AllowSkip。' -ForegroundColor Red
    exit 1
}
if ($failed.Count -gt 0) {
    Write-Host ("界面验证失败：" + (($failed | ForEach-Object { $_.Version }) -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host ("界面验证全部通过（$($results.Count) 个）。") -ForegroundColor Green
