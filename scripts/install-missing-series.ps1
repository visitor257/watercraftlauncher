# 把「一个 jar 盖一个 1.xx 系列」需要逐个真窗口验证的实例补齐。
#
# 为什么每个补丁版都要一个实例：一个 jar 在系列内兼容 ≠ 每个版本都能跑。
# 系列内部有签名分裂（1.20.2 加了横向滚动参数、1.19.3 改了 renderBackground、
# 1.20.5 换组件系统…），只有真起一遍才知道哪个版本被漏了。
#
# 可续跑：已存在的实例直接跳过。

param(
    [string[]]$Versions = @(
        '1.16', '1.16.1', '1.16.2', '1.16.3', '1.16.4',
        '1.17',
        '1.18', '1.18.1',
        '1.19', '1.19.1', '1.19.2', '1.19.3',
        '1.20', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6'
    ),
    [string]$LogFile = ''
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$testgame = Join-Path $root 'testgame'
$java = 'D:\Program Files\Java\jdk-21.0.7\bin\java.exe'
if (-not $LogFile) { $LogFile = Join-Path $root 'logs\build\install-series.log' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogFile) | Out-Null

function Say([string]$m) {
    $line = "[$(Get-Date -Format 'HH:mm:ss')] $m"
    Write-Host $line
    Add-Content -Path $LogFile -Value $line -Encoding UTF8
}

foreach ($v in $Versions) {
    $exists = $false
    foreach ($cand in @("V$v-FABRIC", "fabric-loader-0.20.1-$v")) {
        if (Test-Path (Join-Path $testgame "versions\$cand")) { $exists = $true; break }
    }
    if ($exists) { Say "$v 已有实例，跳过"; continue }

    Say "=== 安装 $v"
    # 超时靠外层：单版本给 15 分钟够（下载 MC + assets + 库 + loader）
    $out = & $java -cp (Join-Path $root 'tools\probe\out') dev.mcver.probe.ProbeInstall `
        $testgame $v FABRIC - NONE - - 0 2>&1
    $ok = ($out | Select-String '\[probe\] OK').Count
    $line = ($out | Select-String '\[probe\]').Line | Select-Object -Last 1
    if ($ok -gt 0) {
        Say "$v OK  $line"
        # 装上隔离标记：没它日志会落到 testgame\logs，像素验证找不到本轮日志
        foreach ($cand in @("fabric-loader-0.20.1-$v", "V$v-FABRIC")) {
            $d = Join-Path $testgame "versions\$cand"
            if (Test-Path $d) { New-Item -ItemType File -Force -Path (Join-Path $d '.mcver-isolated') | Out-Null }
        }
    } else {
        Say "$v FAIL  $line"
        $out | Select-Object -Last 12 | ForEach-Object { Add-Content -Path $LogFile -Value ('    ' + $_) -Encoding UTF8 }
    }
}

Say '安装阶段结束'
