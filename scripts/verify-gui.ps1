# 界面级验证：真实窗口抓图 + 数面板特征像素
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\verify-gui.ps1 [-Targets 1.19.4,1.20.1]
#
# 为什么要这么麻烦（2026-10-26 的教训）：
#   - 日志里出现 screen=MAIN 只证明 init() 跑过，不证明画出来了；
#   - 无头环境下 ScreenshotRecorder 早期抓到的是启动画面，会骗人；
#   - 所以这里起真进程 → 等够首帧 → PrintWindow 抓真窗口 → 数 #C6C6C6（面板色）。
#
# 判据：中心点必须是 C6C6C6，且 4px 采样的命中数在 1 万以上（四个目标都稳定在 ~15000）。

param(
    [string[]]$Targets = @('1.21.4', '1.21.1', '1.20.1', '1.19.4', '1.18.2', '1.17.1'),
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
}

# Fabric API 版本（下载到 testgame\.cache，缺什么补什么）
$fabricApi = @{
    '1.21.4' = @{ v = '0.119.4+1.21.4'; url = '' }
    '1.21.1' = @{ v = '0.116.17+1.21.1'; url = '' }
    '1.20.1' = @{ v = '0.92.2+1.20.1';  url = '' }
    '1.19.4' = @{ v = '0.87.2+1.19.4';  url = '' }
    '1.18.2' = @{ v = '0.77.0+1.18.2';  url = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/qk28POfr/fabric-api-0.77.0%2B1.18.2.jar' }
    '1.17.1' = @{ v = '0.46.1+1.17';   url = 'https://cdn.modrinth.com/data/P7dR8mSH/versions/0.46.1%2B1.17/fabric-api-0.46.1%2B1.17.jar' }
}

# Modrinth 上没现成 url 的就现查一次（查不到就报错，不静默往下走）
function Resolve-FabricApiUrl([string]$ver) {
    $enc = [uri]::EscapeDataString($ver)
    $json = curl.exe -sS "https://api.modrinth.com/v2/project/fabric-api/version/$enc" | ConvertFrom-Json
    if (-not $json.files -or $json.files.Count -eq 0) { throw "Modrinth 上没有 fabric-api $ver" }
    return $json.files[0].url
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
    $gameLog = $null
    for ($i = 0; $i -lt 90; $i++) {
        $gameLog = Get-ChildItem (Join-Path $testgame "versions\$id\logs") -Filter 'mcver-launch-*.log' -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($gameLog) {
            $hit = Select-String -Path $gameLog.FullName -Pattern 'screen=MAIN chrome' -ErrorAction SilentlyContinue
            if ($hit) { break }
        }
        Start-Sleep -Seconds 1
    }
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

    # children= 是控件个数，用来间接确认按钮确实建出来了（Btn 适配层）
    $children = '-'
    if ($gameLog) {
        $m = Select-String -Path $gameLog.FullName -Pattern 'children=(\d+)' | Select-Object -Last 1
        if ($m) { $children = $m.Matches[0].Groups[1].Value }
    }

    $ok = ($center -eq 'C6C6C6' -and $hits -gt 10000)
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[$v] center=$center hits=$hits children=$children -> " + $(if ($ok) { 'OK' } else { 'FAIL' })) -ForegroundColor $color
    $results += [pscustomobject]@{ Version = $v; Center = $center; Hits = $hits; Children = $children; Ok = $ok }

    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($proc -and -not $proc.HasExited) { $proc.Kill() }
    Start-Sleep -Seconds 2
}

Write-Host ''
$results | Format-Table -AutoSize
if (($results | Where-Object { -not $_.Ok }).Count -gt 0) { exit 1 }
Write-Host '界面验证全部通过。' -ForegroundColor Green
