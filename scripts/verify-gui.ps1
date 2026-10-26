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
    [string[]]$Targets = @('1.21.11', '1.21.10', '1.21.9', '1.21.4', '1.21.1', '1.20.1', '1.19.4', '1.18.2', '1.17.1', '1.16.5'),
    [int]$WarmupSec = 40,
    [string]$JavaHome21 = 'D:\Program Files\Java\jdk-21.0.7'
)

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
    '1.17.1' = 'fabric-loader-0.20.1-1.17.1'
    '1.21.11' = 'v12111'
    '1.21.10' = 'v12110'
    '1.21.9' = 'v1219'
}

# Fabric API 版本（下载到 testgame\.cache，缺什么补什么）
$fabricApi = @{
    '1.21.11' = @{ v = '0.141.6+1.21.11'; url = '' }
    '1.21.10' = @{ v = '0.138.4+1.21.10'; url = '' }
    '1.21.9' = @{ v = '0.134.1+1.21.9'; url = '' }
    '1.21.4' = @{ v = '0.119.4+1.21.4'; url = '' }
    '1.21.1' = @{ v = '0.116.17+1.21.1'; url = '' }
    '1.20.1' = @{ v = '0.92.2+1.20.1';  url = '' }
    '1.19.4' = @{ v = '0.87.2+1.19.4';  url = '' }
    '1.18.2' = @{ v = '0.77.0+1.18.2';  url = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/qk28POfr/fabric-api-0.77.0%2B1.18.2.jar' }
    '1.17.1' = @{ v = '0.46.1+1.17';   url = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/0.46.1%2B1.17/fabric-api-0.46.1%2B1.17.jar' }
    '1.16.5' = @{ v = '0.42.0+1.16';   url = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/0.42.0%2B1.16/fabric-api-0.42.0%2B1.16.jar' }
}

# Modrinth 上没现成 url 的就现查一次（查不到就报错，不静默往下走）
function Resolve-FabricApiUrl([string]$ver) {
    $enc = [uri]::EscapeDataString($ver)
    $json = curl.exe -sS "https://api.modrinth.com/v2/project/fabric-api/version/$enc" | ConvertFrom-Json
    if (-not $json.files -or $json.files.Count -eq 0) { throw "Modrinth 上没有 fabric-api $ver" }
    return $json.files[0].url
}

# 首次启动的无障碍引导屏（1.21.9+ 的 AccessibilityOnboardingScreen）会挡在 TitleScreen 前面，
# 自检钩子等不到界面 → 先把它关掉。注意：只改这一行，其它选项原样保留。
function Disable-Onboarding([string]$id) {
    $opt = Join-Path $testgame "versions\$id\options.txt"
    if (-not (Test-Path $opt)) { return }
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

    $jar = Join-Path $root "dist\watercraftlauncher-fabric-1.0.0-mc$v.jar"
    if (-not (Test-Path $jar)) { throw "没有构建产物 $jar" }
    Copy-Item $jar $mods -Force

    if (-not (Get-ChildItem $mods -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue)) {
        $meta = $fabricApi[$v]
        if (-not $meta) { throw "不知道 MC $v 该用哪个 fabric-api" }
        $cache = Join-Path $testgame '.cache'
        New-Item -ItemType Directory -Force -Path $cache | Out-Null
        $name = "fabric-api-$($meta.v).jar"
        $target = Join-Path $cache $name
        if (-not (Test-Path $target)) {
            $url = $meta.url
            if (-not $url) { $url = Resolve-FabricApiUrl $meta.v }
            Write-Host "  下载 fabric-api $($meta.v)…"
            curl.exe -sSL -o $target $url
        }
        if ((Get-Item $target).Length -lt 500000) { throw "fabric-api 下载不对（$(Get-Item $target).Length 字节）：$target" }
        Copy-Item $target $mods -Force
    }
}

$results = @()
foreach ($v in $Targets) {
    $id = "V$v-FABRIC"
    if ($instanceOverride.ContainsKey($v)) { $id = $instanceOverride[$v] }
    if (-not (Test-Path (Join-Path $testgame "versions\$id"))) {
        Write-Host "[$v] 跳过：没有 $id" -ForegroundColor Yellow
        continue
    }

    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Seconds 2

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
if ($failed.Count -gt 0) {
    Write-Host ("界面验证失败：" + (($failed | ForEach-Object { $_.Version }) -join ', ')) -ForegroundColor Red
    exit 1
}
Write-Host ("界面验证全部通过（$($results.Count) 个）。") -ForegroundColor Green
