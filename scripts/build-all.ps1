# 构建所有目标版本：单源码 + src/compat/<name>/java 覆盖
#
# 用法：powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1
# 产物先落在 mod\build\libs，再复制到 dist\。
#
# 加新目标版本只需要三步：
#   1) 在 $targets 里加一行（mc / yarn / fabric-api 版本）
#   2) 如果 API 有差异，把有差异的「整文件」放到 mod\src\compat\<name>\java\ 对应包路径下
#   3) 如果没差异，compat 留空即可（直接复用主源码）

param(
    [string]$Gradle = '',
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$mod  = Join-Path $root 'mod'
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null

# ---- 找 Gradle 8.x ------------------------------------------------------------
# 以前这里写死 .tools\gradle-8.12\bin\gradle.bat，换机器 / 打包后没这个目录就第一步卡死。
# 现在按顺位找：显式参数 > GRADLE_HOME > .tools\gradle-8.12 > PATH > wrapper 缓存里已解压的。
function Find-Gradle {
    if ($Gradle -ne '') {
        if (Test-Path $Gradle) { return (Resolve-Path $Gradle).Path }
        throw "-Gradle 指定的路径不存在：$Gradle"
    }
    $gh = $env:GRADLE_HOME
    if ($gh -and (Test-Path (Join-Path $gh 'bin\gradle.bat'))) { return (Join-Path $gh 'bin\gradle.bat') }

    $tools = Join-Path $root '.tools\gradle-8.12\bin\gradle.bat'
    if (Test-Path $tools) { return $tools }

    $cmd = Get-Command gradle -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source) { return $cmd.Source }

    # wrapper 缓存：~/.gradle/wrapper/dists/gradle-8.*/*/gradle-8.*/bin/gradle.bat
    $dists = Join-Path $env:USERPROFILE '.gradle\wrapper\dists'
    if (Test-Path $dists) {
        $hit = Get-ChildItem $dists -Directory -Filter 'gradle-8.*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { Get-ChildItem $_.FullName -Recurse -Filter 'gradle.bat' -ErrorAction SilentlyContinue } |
            Where-Object { $_.FullName -match '\\bin\\gradle\.bat$' } |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

$gradle = Find-Gradle
if ($null -eq $gradle) {
    Write-Host '找不到 Gradle 8.x。三条路：' -ForegroundColor Red
    Write-Host '  1) 装一个 gradle 8.x 并放进 PATH；'
    Write-Host '  2) 解压一份到 <项目根>\.tools\gradle-8.12\（HANDOVER 1.2 有下载地址）；'
    Write-Host '  3) 用 mod\gradlew.bat（先把 distributionUrl 换成腾讯镜像）。'
    exit 1
}
Write-Host "Gradle: $gradle" -ForegroundColor Cyan

$targets = @(
    @{ name = '1.21.4'; mc = '1.21.4'; yarn = '1.21.4+build.8'; api = '0.119.4+1.21.4'; compat = '' },
    @{ name = '1.21.1'; mc = '1.21.1'; yarn = '1.21.1+build.3'; api = '0.116.17+1.21.1'; compat = '1.21.1' }
    # @{ name = '1.20.1'; mc = '1.20.1'; yarn = '1.20.1+build.10'; api = '0.92.2+1.20.1'; compat = '1.20.1' },
)

$failed = @()
foreach ($t in $targets) {
    Write-Host "=== 构建 MC $($t.name) ===" -ForegroundColor Cyan
    $gargs = @(
        'build', '--console=plain',
        "-Pminecraft_version=$($t.mc)",
        "-Pyarn_mappings=$($t.yarn)",
        "-Pfabric_api_version=$($t.api)"
    )
    if ($Offline) { $gargs += '--offline' }
    if ($t.compat -ne '') { $gargs += "-Pcompat=$($t.compat)" }

    Push-Location $mod
    try {
        & $gradle @gargs
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($code -ne 0) {
        Write-Host "  失败：MC $($t.name)" -ForegroundColor Red
        $failed += $t.name
        continue
    }

    $jar = Get-ChildItem (Join-Path $mod 'build\libs') -Filter "*-mc$($t.mc).jar" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $jar) {
        Write-Host "  没找到产物 jar" -ForegroundColor Red
        $failed += $t.name
        continue
    }
    Copy-Item $jar.FullName (Join-Path $dist $jar.Name) -Force
    Write-Host "  -> dist\$($jar.Name)" -ForegroundColor Green
}

Write-Host ''
if ($failed.Count -gt 0) {
    Write-Host "以下目标失败：$($failed -join ', ')" -ForegroundColor Red
    exit 1
}
Write-Host '全部目标构建完成。' -ForegroundColor Green
Get-ChildItem $dist -Filter *.jar | Select-Object Name, Length | Format-Table -AutoSize
