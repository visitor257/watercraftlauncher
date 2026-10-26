# Build probes into an ALTERNATE output dir, so a new feature can be verified
# while the verification matrix is still running (build-probe.ps1 takes a lock
# because deleting in-use .class files breaks running probe JVMs).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out  = Join-Path $root 'tools\probe\out-alt'
if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $out | Out-Null

$core  = (Get-ChildItem (Join-Path $root 'mod\src\main\java\dev\mcver\core') -Filter *.java |
          Where-Object { $_.Name -notin @('Compat.java', 'Log.java') }).FullName
$probe = (Get-ChildItem (Join-Path $root 'tools\probe') -Recurse -Filter *.java |
          Where-Object { $_.FullName -notmatch '\\out' }).FullName

$javac = 'D:\Program Files\Java\jdk-21.0.7\bin\javac.exe'
& $javac -encoding UTF-8 --release 21 -nowarn -d $out @($core + $probe) 2>&1 | Select-Object -First 20
if ($LASTEXITCODE -ne 0) { Write-Host 'compile failed' -ForegroundColor Red; exit 1 }
Write-Host "probe build OK -> $out" -ForegroundColor Green
