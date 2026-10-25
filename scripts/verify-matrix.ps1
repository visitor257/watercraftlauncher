# 一键验证矩阵（L3）：装 + 启，跑的是生产代码，输出可复跑的结果表
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\verify-matrix.ps1                 # 跑全部
#   ... -Phase 1              # 只跑某一段（见下方 phase 注释）
#   ... -Only 1.21.4          # 只跑 MC 版本匹配的（子串匹配）
#   ... -Only FABRIC          # 只要某个加载器
#   ... -Force                # 重跑已通过的
#   ... -SkipLaunch           # 只验安装
#   ... -GameDir D:\wcverify  # 换测试目录
#
# 结果：
#   verification\results.csv      每个组合一行，边跑边写（中断后可续）
#   verification\RESULTS.md       汇总表（每次运行重写）
#   logs\verify\<combo>.*.log     原始输出
#
# 判定：安装=InstallJob 到 DONE 且 isComplete；启动=日志出现 "Setting user"。
# 离线账号的 401 是正常的，不算失败。

param(
    [string]$GameDir = '',
    [string]$Only = '',
    [string]$Combo = '',
    [int]$Phase = 0,
    [int]$Max = 0,
    [int]$LaunchTimeout = 300,
    [switch]$SkipLaunch,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if ($GameDir -eq '') { $GameDir = Join-Path $root 'testgame' }
New-Item -ItemType Directory -Force -Path $GameDir | Out-Null

$cp = Join-Path $root 'tools\probe\out'
if (-not (Test-Path (Join-Path $cp 'dev\mcver\probe\ProbeInstall.class'))) {
    Write-Host '探针还没编译，先跑 scripts\build-probe.ps1' -ForegroundColor Red
    exit 1
}

$resDir  = Join-Path $root 'verification'
$logDir  = Join-Path $root 'logs\verify'
New-Item -ItemType Directory -Force -Path $resDir, $logDir | Out-Null
$csv = Join-Path $resDir 'results.csv'

# ---------------------------------------------------------------- 矩阵
# phase 1 = 1.20+ 现代段（含 26.x 边界）；2 = 1.14~1.19 中段；3 = 1.14 以前远古段；
# 4 = OptiFine / Iris 光影；每个加载器都按"纪元边界"取样，不是全量 192 个组合。
$matrix = @(
    # ---- Fabric（规则 >= 1.14）------------------------------------------------
    @{ mc='1.14';   kind='FABRIC'; shader='NONE'; phase=2; note='Fabric 下限' },
    @{ mc='1.14.4'; kind='FABRIC'; shader='NONE'; phase=2; note='' },
    @{ mc='1.15.2'; kind='FABRIC'; shader='NONE'; phase=2; note='' },
    @{ mc='1.16.5'; kind='FABRIC'; shader='NONE'; phase=2; note='' },
    @{ mc='1.17';   kind='FABRIC'; shader='NONE'; phase=2; note='lwjgl3.2' },
    @{ mc='1.18.2'; kind='FABRIC'; shader='NONE'; phase=2; note='' },
    @{ mc='1.19.4'; kind='FABRIC'; shader='NONE'; phase=2; note='' },
    @{ mc='1.20.1'; kind='FABRIC'; shader='NONE'; phase=1; note='' },
    @{ mc='1.20.4'; kind='FABRIC'; shader='NONE'; phase=1; note='' },
    @{ mc='1.20.5'; kind='FABRIC'; shader='NONE'; phase=1; note='组件系统断层' },
    @{ mc='1.20.6'; kind='FABRIC'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.1'; kind='FABRIC'; shader='NONE'; phase=1; note='mod target' },
    @{ mc='1.21.4'; kind='FABRIC'; shader='NONE'; phase=1; note='mod target / 日常' },
    @{ mc='1.21.5'; kind='FABRIC'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.8'; kind='FABRIC'; shader='NONE'; phase=1; note='' },
    @{ mc='26.1';   kind='FABRIC'; shader='NONE'; phase=1; note='新命名' },

    # ---- Quilt（>= 1.14.4）----------------------------------------------------
    @{ mc='1.14.4'; kind='QUILT'; shader='NONE'; phase=2; note='Quilt 下限' },
    @{ mc='1.18.2'; kind='QUILT'; shader='NONE'; phase=2; note='' },
    @{ mc='1.20.1'; kind='QUILT'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.4'; kind='QUILT'; shader='NONE'; phase=1; note='' },

    # ---- Forge（精确集合）-----------------------------------------------------
    @{ mc='1.7.10'; kind='FORGE'; shader='NONE'; phase=3; note='远古 / Java 8' },
    @{ mc='1.10.2'; kind='FORGE'; shader='NONE'; phase=3; note='远古 / Java 8' },
    @{ mc='1.12.2'; kind='FORGE'; shader='NONE'; phase=3; note='已过 / Java 8' },
    @{ mc='1.14.4'; kind='FORGE'; shader='NONE'; phase=2; note='' },
    @{ mc='1.16.5'; kind='FORGE'; shader='NONE'; phase=2; note='' },
    @{ mc='1.17.1'; kind='FORGE'; shader='NONE'; phase=2; note='' },
    @{ mc='1.18.2'; kind='FORGE'; shader='NONE'; phase=2; note='' },
    @{ mc='1.19.4'; kind='FORGE'; shader='NONE'; phase=2; note='' },
    @{ mc='1.20.1'; kind='FORGE'; shader='NONE'; phase=1; note='已过' },
    @{ mc='1.20.2'; kind='FORGE'; shader='NONE'; phase=1; note='已过' },
    @{ mc='1.20.4'; kind='FORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.20.6'; kind='FORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.1'; kind='FORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.4'; kind='FORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='26.1';   kind='FORGE'; shader='NONE'; phase=1; note='新命名 / 未测' },

    # ---- NeoForge（>= 1.20.1）-------------------------------------------------
    @{ mc='1.20.1'; kind='NEOFORGE'; shader='NONE'; phase=1; note='已过' },
    @{ mc='1.20.2'; kind='NEOFORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.20.4'; kind='NEOFORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.20.6'; kind='NEOFORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.1'; kind='NEOFORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='1.21.4'; kind='NEOFORGE'; shader='NONE'; phase=1; note='' },
    @{ mc='26.1';   kind='NEOFORGE'; shader='NONE'; phase=1; note='新命名 / 未测' },

    # ---- OptiFine / Iris（光影）------------------------------------------------
    @{ mc='1.12.2'; kind='NONE'; shader='OPTIFINE'; phase=4; note='' },
    @{ mc='1.19.4'; kind='NONE'; shader='OPTIFINE'; phase=4; note='' },
    @{ mc='1.20.1'; kind='NONE'; shader='OPTIFINE'; phase=4; note='' },
    @{ mc='1.21.4'; kind='NONE'; shader='OPTIFINE'; phase=4; note='' },
    @{ mc='1.20.1'; kind='FABRIC'; shader='IRIS'; phase=4; note='Iris 需 Fabric' },
    @{ mc='1.21.4'; kind='FABRIC'; shader='IRIS'; phase=4; note='Iris 需 Fabric' }
)

# ---------------------------------------------------------------- 工具函数
function Get-Done {
    if (-not (Test-Path $csv)) { return @() }
    return @(Import-Csv $csv)
}

function Save-Row($row) {
    $fields = @('combo','mc','kind','shader','install','launch','version','seconds','detail','when')
    if (-not (Test-Path $csv)) {
        ($fields -join ',') | Set-Content -Encoding UTF8 $csv
    }
    $line = ($fields | ForEach-Object {
        $v = [string]$row.$_
        if ($v -match '[,"\r\n]') { '"' + ($v -replace '"', '""') + '"' } else { $v }
    }) -join ','
    Add-Content -Encoding UTF8 -Path $csv -Value $line
}

$done = @(Get-Done)
$java = (Get-Command java -ErrorAction Stop).Source

$sel = @($matrix | Where-Object {
    ($Phase -eq 0 -or $_.phase -eq $Phase) -and
    ($Only -eq '' -or $_.mc -like "*$Only*" -or $_.kind -like "*$Only*" -or $_.shader -like "*$Only*") -and
    ($Combo -eq '' -or "$($_.mc)-$($_.kind)-$($_.shader)" -eq $Combo)
})
if ($Max -gt 0) { $sel = @($sel | Select-Object -First $Max) }

Write-Host "共 $($sel.Count) 个组合，测试目录 $GameDir" -ForegroundColor Cyan
$n = 0
foreach ($c in $sel) {
    $n++
    $combo = "$($c.mc)-$($c.kind)-$($c.shader)"
    $target = "V$($c.mc)-$($c.kind)$(if ($c.shader -ne 'NONE') { '-' + $c.shader })"

    $prev = $done | Where-Object { $_.combo -eq $combo } | Select-Object -Last 1
    if (-not $Force -and $prev -and $prev.install -eq 'OK' -and ($SkipLaunch -or $prev.launch -eq 'OK')) {
        Write-Host "[$n/$($sel.Count)] $combo 已通过，跳过（-Force 重跑）" -ForegroundColor DarkGray
        continue
    }

    Write-Host "[$n/$($sel.Count)] $combo 安装中 $($c.note)" -ForegroundColor Yellow
    $t0 = Get-Date
    $ilog = Join-Path $logDir "$combo.install.log"

    $out = & $java -cp $cp dev.mcver.probe.ProbeInstall $GameDir $c.mc $c.kind '-' $c.shader '-' $target 1 2>&1
    $code = $LASTEXITCODE
    $out | Set-Content -Encoding UTF8 $ilog

    $installedId = $null
    $detail = ''
    foreach ($l in $out) {
        if ($l -match '^\[probe\] OK (.+)$') { $installedId = $Matches[1].Trim() }
        if ($l -match '^\[probe\] ERROR (.+)$') { $detail = $Matches[1].Trim() }
        if ($l -match '^\[probe\] SKIP: (.+)$') { $detail = $Matches[1].Trim() }
    }

    $install = 'FAIL'
    if ($code -eq 0) { $install = 'OK' }
    elseif ($code -eq 3) { $install = 'SKIP' }
    elseif ($detail -eq '') { $detail = "退出码 $code" }

    $launch = '-'
    if ($install -eq 'OK' -and -not $SkipLaunch) {
        Write-Host "        启动验证 $installedId ..." -ForegroundColor Yellow
        $llog = Join-Path $logDir "$combo.launch.log"
        $lout = & $java -cp $cp dev.mcver.probe.ProbeLaunch $GameDir $installedId $LaunchTimeout 2>&1
        $lcode = $LASTEXITCODE
        $lout | Set-Content -Encoding UTF8 $llog
        $launch = if ($lcode -eq 0) { 'OK' } else { 'FAIL' }
        foreach ($l in $lout) {
            if ($l -match '^\[probe\] (ERROR|TIMEOUT|进程已退出) ?(.*)$') { $detail = ($l -replace '^\[probe\] ', '').Trim() }
        }
    }

    $secs = [int]((Get-Date) - $t0).TotalSeconds
    $row = [pscustomobject]@{
        combo   = $combo
        mc      = $c.mc
        kind    = $c.kind
        shader  = $c.shader
        install = $install
        launch  = $launch
        version = $installedId
        seconds = $secs
        detail  = $detail
        when    = (Get-Date -Format 'yyyy-MM-dd HH:mm')
    }
    Save-Row $row

    $color = if ($install -eq 'OK' -and ($launch -eq 'OK' -or $launch -eq '-')) { 'Green' } else { 'Red' }
    Write-Host "        => install=$install launch=$launch ${secs}s $detail" -ForegroundColor $color
}

# ---------------------------------------------------------------- 汇总
$all = Get-Done
$md = New-Object System.Collections.Generic.List[string]
$md.Add('# 验证结果（自动生成）')
$md.Add('')
$md.Add("生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm')  ·  测试目录：$GameDir")
$md.Add('')
$md.Add('| MC | 加载器 | 光影 | 安装 | 启动 | 版本 id | 耗时 | 备注 |')
$md.Add('|---|---|---|---|---|---|---|---|')
foreach ($r in $all) {
    $md.Add("| $($r.mc) | $($r.kind) | $($r.shader) | $($r.install) | $($r.launch) | $($r.version) | $($r.seconds)s | $($r.detail) |")
}
$md.Add('')
$ok = @($all | Where-Object { $_.install -eq 'OK' -and $_.launch -eq 'OK' }).Count
$md.Add("L2（装 + 启）通过：**$ok** / $($all.Count)")
$md | Set-Content -Encoding UTF8 (Join-Path $resDir 'RESULTS.md')

Write-Host ''
Write-Host "L2 通过 $ok / $($all.Count)，结果见 verification\RESULTS.md" -ForegroundColor Cyan
