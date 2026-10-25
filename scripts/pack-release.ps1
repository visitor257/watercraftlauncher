$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$root = Split-Path -Parent $PSScriptRoot
$zip  = Join-Path (Split-Path -Parent $root) ('watercraft-launcher-' + (Get-Date -Format 'yyyy-MM-dd') + '.zip')
if (Test-Path $zip) { Remove-Item -LiteralPath $zip -Force }

$excludeDirs = @('.tools','testgame','_keep','logs','build','out','.git',
  'mod\build','mod\.gradle','mod\run','mod\config','mod\out','tools\probe\out')
$excludeFiles = @('*.bak','*.bak2','*.bak3','*.retired-*','*.log','hs_err_pid*','*.zip',
  'config\mcver.json','mod\config\mcver.json','build-all.log')

$all = Get-ChildItem -LiteralPath $root -Recurse -File -Force
$keep = @()
foreach ($f in $all) {
  $rel = $f.FullName.Substring($root.Length + 1)
  $skip = $false
  foreach ($d in $excludeDirs) {
    if ($rel -eq $d -or $rel.StartsWith($d + '\')) { $skip = $true; break }
  }
  if (-not $skip) {
    foreach ($p in $excludeFiles) {
      if ($f.Name -like $p -or $rel -like $p) { $skip = $true; break }
    }
  }
  if (-not $skip) { $keep += $f }
}

"files to pack: $($keep.Count)"
$archive = [System.IO.Compression.ZipFile]::Open($zip, 'Create')
try {
  foreach ($f in $keep) {
    $rel = $f.FullName.Substring($root.Length + 1)
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $f.FullName, $rel, [System.IO.Compression.CompressionLevel]::Optimal)
  }
} finally { $archive.Dispose() }

$z = Get-Item -LiteralPath $zip
"zip: $($z.FullName)"
"size: {0:N2} MB" -f ($z.Length / 1MB)

# verify
$zr = [System.IO.Compression.ZipFile]::OpenRead($zip)
try { "entries: $($zr.Entries.Count)" } finally { $zr.Dispose() }
