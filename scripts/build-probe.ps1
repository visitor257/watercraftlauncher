# 编译离线探针：core（生产代码）+ tools/probe（探针与 MinecraftClient 桩）
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build-probe.ps1
# 产物：tools\probe\out\  （可直接 java -cp 跑）
#
# 为什么能这么干：core 层只有 Config.dir() 引用 net.minecraft.client.MinecraftClient，
# 一个桩（tools\probe\net\minecraft\client\MinecraftClient.java）就够。
# 这样验证"装得上、起得来"不用进游戏，快一个数量级。

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# 矩阵在跑的时候重编 = 把正在用的 .class 删掉，后面每个探针 JVM 都会
# NoClassDefFoundError（踩过：整个 Phase 2 被自己编成 11 个假失败）。
$lock = Join-Path $root 'verification\.run.lock'
if (Test-Path $lock) {
    $holder = Get-Content $lock -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($holder -and (Get-Process -Id $holder -ErrorAction SilentlyContinue)) {
        Write-Host "验证矩阵正在跑（PID $holder），先停下再编译" -ForegroundColor Red
        exit 1
    }
}

$javac = (Get-Command javac -ErrorAction SilentlyContinue)
if ($null -eq $javac) {
    Write-Host '找不到 javac：需要 JDK 21（编译目标 release=21）' -ForegroundColor Red
    exit 1
}

$core  = (Get-ChildItem (Join-Path $root 'mod\src\main\java\dev\mcver\core') -Filter *.java).FullName
$probe = (Get-ChildItem (Join-Path $root 'tools\probe') -Recurse -Filter *.java |
          Where-Object { $_.FullName -notmatch '\\out\\' }).FullName

# 去 BOM：PowerShell 5.1 的 Set-Content -Encoding UTF8 会写 BOM，javac 直接报「非法字符 \ufeff」。
foreach ($f in @($core + $probe)) {
    $bytes = [System.IO.File]::ReadAllBytes($f)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Write-Host "去 BOM: $([System.IO.Path]::GetFileName($f))" -ForegroundColor DarkYellow
        [System.IO.File]::WriteAllBytes($f, $bytes[3..($bytes.Length - 1)])
    }
}

$out = Join-Path $root 'tools\probe\out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Remove-Item (Join-Path $out '*') -Recurse -Force -ErrorAction SilentlyContinue

# ---- OptiFine 驱动：必须单独编成 Java 8 字节码 ----------------------------------
# 1.12.2 的 OptiFine 安装器只能在 Java 8 上跑，而 core 是按 release 21 编的。
# 这里先编一份 runner，打成 tools\probe\out\optifine-runner.jar，运行时当资源解出来用。
$runnerSrc = Join-Path $root 'mod\runner'
$runnerOut = Join-Path $root 'tools\probe\out-runner'
if (Test-Path $runnerSrc) {
    Remove-Item $runnerOut -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $runnerOut | Out-Null
    $runnerFiles = @((Get-ChildItem $runnerSrc -Recurse -Filter *.java).FullName)
    foreach ($f in $runnerFiles) {
        $bytes = [System.IO.File]::ReadAllBytes($f)
        if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
            [System.IO.File]::WriteAllBytes($f, $bytes[3..($bytes.Length - 1)])
        }
    }
    Write-Host "编译 OptiFine 驱动（release 8）..." -ForegroundColor Cyan
    & javac -Xlint:-options -nowarn -encoding UTF-8 --release 8 -d $runnerOut $runnerFiles 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Host 'OptiFine 驱动编译失败' -ForegroundColor Red; exit 1 }
    $runnerJar = Join-Path $out 'optifine-runner.jar'
    Remove-Item $runnerJar -Force -ErrorAction SilentlyContinue
    & jar --create --file $runnerJar -C $runnerOut .
    if ($LASTEXITCODE -ne 0) { Write-Host 'OptiFine 驱动打包失败' -ForegroundColor Red; exit 1 }
}

Write-Host "编译 $($core.Count) 个 core 文件 + $($probe.Count) 个探针文件 ..." -ForegroundColor Cyan
& javac -encoding UTF-8 -d $out @($core + $probe)
if ($LASTEXITCODE -ne 0) {
    Write-Host '编译失败' -ForegroundColor Red
    exit 1
}
Write-Host "OK -> $out" -ForegroundColor Green
