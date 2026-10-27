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
    [switch]$Offline,
    # 只构建指定的 target（例：-Only 1.19,1.19.3）。
    # 注意：-File 模式下 "1.19,1.19.3" 会整个当一个字符串传进来，下面自己拆。
    # 以前根本没有这个参数，传了也会被忽略 —— 结果「只跑两个」其实是全跑。
    [string]$Only = '',
    # 每次用完就杀 daemon：连续跑七目标时 daemon 会越积越多，最后互相挤爆内存
    [switch]$NoDaemon
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
    # 一个 jar 盖一整个 1.xx 系列。编译基准 = 该系列里「不抬高 Java 要求」的最新补丁版：
    #   1.20.5/1.20.6 要 Java 21，不能当 1.20 的基准（会把 1.20.1 的用户挡在门外），
    #   它们靠 NoSuchMethodError / 静态分支退回；1.21 反过来必须站在最新的 1.21.11 上
    #   （Item.Settings#registryKey 是 1.21.2 才有的）。
    @{ name = '1.21'; mc = '1.21.11'; yarn = '1.21.11+build.6'; api = '0.141.6+1.21.11'; compat = '';    java = '21'; wide = '1.21' },
    @{ name = '1.20'; mc = '1.20.4';  yarn = '1.20.4+build.3';  api = '0.97.1+1.20.4';   compat = '1.20';  java = '17'; wide = '1.20' },
    # 1.19 系列内部有真断层（Registries 字段类型 + 渲染签名 + 创造栅 API），拆两个 jar
    @{ name = '1.19'; mc = '1.19.2';  yarn = '1.19.2+build.10'; api = '0.77.0+1.19.2';   compat = '1.19';  java = '17'; wide = '1.19'; wideMax = '1.19.3' },
    @{ name = '1.19.3'; mc = '1.19.4'; yarn = '1.19.4+build.2'; api = '0.87.2+1.19.4';  compat = '1.19.3'; java = '17'; wide = '1.19.3' },
    @{ name = '1.18'; mc = '1.18.2';  yarn = '1.18.2+build.4';  api = '0.77.0+1.18.2';   compat = '1.18';  java = '17'; wide = '1.18' },
    @{ name = '1.17'; mc = '1.17.1';  yarn = '1.17.1+build.65'; api = '0.46.1+1.17';     compat = '1.17';  java = '16'; wide = '1.17' },
    @{ name = '1.16'; mc = '1.16.5';  yarn = '1.16.5+build.10'; api = '0.42.0+1.16';     compat = '1.16';  java = '8';  wide = '1.16' },
    # 1.15 / 1.14 是 Fabric 官方支持的下限（再往下是 Legacy Fabric，另一套 mappings）
    @{ name = '1.15'; mc = '1.15.2';  yarn = '1.15.2+build.17'; api = '0.28.5+1.15';     compat = '1.15';  java = '8';  wide = '1.15' },
    @{ name = '1.14'; mc = '1.14.4';  yarn = '1.14.4+build.18'; api = '0.28.5+1.14';     compat = '1.14';  java = '8';  wide = '1.14' }
)

$failed = @()
if ($Only) {
    $want = @($Only -split '[,\s]+' | Where-Object { $_ -ne '' })
    $targets = @($targets | Where-Object { $want -contains $_.name })
    if ($targets.Count -eq 0) { throw "-Only 里没有一个有效 target：$Only" }
    Write-Host "只构建：$($targets.name -join ', ')" -ForegroundColor Cyan
}
foreach ($t in $targets) {
    Write-Host "=== 构建 MC $($t.name) ===" -ForegroundColor Cyan
    $gargs = @(
        'build', '--console=plain',
        "-Pminecraft_version=$($t.mc)",
        "-Pyarn_mappings=$($t.yarn)",
        "-Pfabric_api_version=$($t.api)",
        "-Pjava_release=$($t.java)"
    )
    if ($Offline) { $gargs += '--offline' }
    if ($NoDaemon) { $gargs += '--no-daemon' }
    if ($t.compat -ne '') { $gargs += "-Pcompat=$($t.compat)" }
    # 一个 jar 盖一整个大版本：让 fabric.mod.json 写 >=1.21 <1.22（由 build.gradle 算，不传 > 符号）
    if ($t.wide -ne '' -and $t.wide) { $gargs += "-Pwide_mc=$($t.wide)" }
    if ($t.wideMax) { $gargs += "-Pwide_max=$($t.wideMax)" }

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

    # 产物名用 target 的 name（1.21 通用 jar 编自 1.21.11，但文件名是 mc1.21）
    $jar = Get-ChildItem (Join-Path $mod 'build\libs') -Filter "*-mc$($t.name).jar" |
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
