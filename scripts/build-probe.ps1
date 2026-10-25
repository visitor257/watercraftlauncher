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

$javac = (Get-Command javac -ErrorAction SilentlyContinue)
if ($null -eq $javac) {
    Write-Host '找不到 javac：需要 JDK 21（编译目标 release=21）' -ForegroundColor Red
    exit 1
}

$core  = (Get-ChildItem (Join-Path $root 'mod\src\main\java\dev\mcver\core') -Filter *.java).FullName
$probe = (Get-ChildItem (Join-Path $root 'tools\probe') -Recurse -Filter *.java |
          Where-Object { $_.FullName -notmatch '\\out\\' }).FullName

$out = Join-Path $root 'tools\probe\out'
New-Item -ItemType Directory -Force -Path $out | Out-Null
Remove-Item (Join-Path $out '*') -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "编译 $($core.Count) 个 core 文件 + $($probe.Count) 个探针文件 ..." -ForegroundColor Cyan
& javac -encoding UTF-8 -d $out @($core + $probe)
if ($LASTEXITCODE -ne 0) {
    Write-Host '编译失败' -ForegroundColor Red
    exit 1
}
Write-Host "OK -> $out" -ForegroundColor Green
