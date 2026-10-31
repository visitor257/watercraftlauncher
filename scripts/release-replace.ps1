# 把 dist/ 里的产物同步到 GitHub release（默认只**演练**，加 -Apply 才真动）
#
#   $env:GH_TOKEN='...'                      # 需要 repo 权限的 token
#   pwsh -File scripts\release-replace.ps1    # 演练：只列要删/要传的
#   pwsh -File scripts\release-replace.ps1 -Apply
#   pwsh -File scripts\release-replace.ps1 -Apply -Verify   # 传完再从远端下载回来比 sha256
#
# 为什么是 PS 而不是 .mjs：这台机器的 TLS 被中间人代理拦了，Node 的 fetch 报
# UNABLE_TO_VERIFY_LEAF_SIGNATURE，而 PowerShell 走 Windows 证书库没问题。
#
# 约定（沿用它上一轮的做法）：**就地替换 v1.0.0 的资产，不新建 tag**；
# 文件名就是资产名；SHA256SUMS 最后传，保证它和前面那批一致。
param(
  [string]$Tag = 'v1.0.0',
  [string]$Repo = 'visitor257/watercraftlauncher',
  [switch]$Apply,
  [switch]$Verify
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
if (-not (Test-Path $dist)) { throw "没有 dist 目录: $dist" }

$tok = $env:GH_TOKEN
if (-not $tok) { $tok = $env:GITHUB_TOKEN }
if (-not $tok -and $Apply) { throw '没有 token：先设 $env:GH_TOKEN（需要 repo 权限）' }
# 演练模式允许匿名读（公开仓库），方便先看清单
$h = @{ 'User-Agent' = 'mcver-release'; Accept = 'application/vnd.github+json' }
if ($tok) { $h['Authorization'] = ('token ' + $tok) }

$rel = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/tags/$Tag" -Headers $h -TimeoutSec 30 -UseBasicParsing
Write-Host ("release {0}: 现有 {1} 个资产" -f $Tag, $rel.assets.Count)

$want = @{}
Get-ChildItem $dist -File | Where-Object { $_.Name -eq 'SHA256SUMS' -or $_.Extension -eq '.jar' } | ForEach-Object {
  $want[$_.Name] = $_
}
Write-Host ("dist 里要发布的: {0} 个（jar {1} + SHA256SUMS）" -f $want.Count, ($want.Keys | Where-Object { $_ -ne 'SHA256SUMS' }).Count)

$remote = @{}
foreach ($a in $rel.assets) { $remote[$a.name] = $a }

$toDelete = @($remote.Keys | Where-Object { -not $want.ContainsKey($_) })
$toUpload = New-Object System.Collections.Generic.List[string]
foreach ($n in $want.Keys) {
  if (-not $remote.ContainsKey($n)) { $toUpload.Add($n); continue }
  $a = $remote[$n]
  $localHash = (Get-FileHash $want[$n].FullName -Algorithm SHA256).Hash.ToLower()
  # 新 API 会返回 digest（sha256:...）；老响应没有就只能比大小
  if ($a.digest) {
    $rh = ($a.digest -replace '^sha256:', '').ToLower()
    if ($rh -ne $localHash) { $toUpload.Add($n) }
  } elseif ($a.size -ne $want[$n].Length) {
    $toUpload.Add($n)
  }
}

Write-Host ''
Write-Host ("要删: {0} 个" -f $toDelete.Count)
$toDelete | Sort-Object | ForEach-Object { Write-Host ("  - " + $_) }
Write-Host ("要传: {0} 个" -f $toUpload.Count)
$toUpload | Sort-Object | ForEach-Object { Write-Host ("  + " + $_) }

if (-not $Apply) {
  Write-Host ''
  Write-Host '这是演练。确认无误后加 -Apply 真跑。' -ForegroundColor Yellow
  return
}

# ---- 真跑：先删多余的，再传新的/变了的，SHA256SUMS 放最后 ----
foreach ($n in ($toDelete | Sort-Object)) {
  $id = $remote[$n].id
  Invoke-RestMethod -Method Delete -Uri "https://api.github.com/repos/$Repo/releases/assets/$id" -Headers $h -TimeoutSec 60 -UseBasicParsing | Out-Null
  Write-Host ("deleted  " + $n)
}

$uploadUrl = $rel.upload_url -replace '\{\?name,label\}$', ''
$ordered = @($toUpload | Where-Object { $_ -ne 'SHA256SUMS' } | Sort-Object)
if ($toUpload -contains 'SHA256SUMS') { $ordered += 'SHA256SUMS' }
foreach ($n in $ordered) {
  # 同名资产必须先删再传：GitHub 对已存在的名字直接 422 already_exists
  if ($remote.ContainsKey($n)) {
    Invoke-RestMethod -Method Delete -Uri "https://api.github.com/repos/$Repo/releases/assets/$($remote[$n].id)" -Headers $h -TimeoutSec 60 -UseBasicParsing | Out-Null
    Write-Host ("replaced " + $n)
  }
  $f = $want[$n].FullName
  $u = '{0}?name={1}' -f $uploadUrl, [uri]::EscapeDataString($n)
  Invoke-RestMethod -Method Post -Uri $u -Headers $h -InFile $f -ContentType 'application/octet-stream' -TimeoutSec 600 -UseBasicParsing | Out-Null
  Write-Host ("uploaded " + $n)
}

if ($Verify) {
  Write-Host ''
  Write-Host '---- 远端下载回来校验 ----'
  $rel2 = Invoke-RestMethod -Uri "https://api.github.com/repos/$Repo/releases/tags/$Tag" -Headers $h -TimeoutSec 30 -UseBasicParsing
  $byName = @{}
  foreach ($a in $rel2.assets) { $byName[$a.name] = $a }
  $ok = 0; $bad = New-Object System.Collections.Generic.List[string]
  foreach ($n in ($want.Keys | Sort-Object)) {
    if (-not $byName.ContainsKey($n)) { $bad.Add($n + ' (远端没有)'); continue }
    $tmp = Join-Path $env:TEMP ('relchk_' + [guid]::NewGuid().ToString('N'))
    Invoke-WebRequest -Uri $byName[$n].browser_download_url -OutFile $tmp -TimeoutSec 600 -UseBasicParsing
    $h1 = (Get-FileHash $tmp -Algorithm SHA256).Hash.ToLower()
    Remove-Item $tmp -Force
    $h2 = (Get-FileHash $want[$n].FullName -Algorithm SHA256).Hash.ToLower()
    if ($h1 -eq $h2) { $ok++ } else { $bad.Add($n) }
  }
  Write-Host ("校验: {0}/{1} 通过" -f $ok, $want.Count)
  if ($bad.Count) { $bad | ForEach-Object { Write-Host ("  MISMATCH " + $_) } ; exit 1 }
}
Write-Host ''
Write-Host ("完成：资产总数应为 {0}" -f $want.Count) -ForegroundColor Green
