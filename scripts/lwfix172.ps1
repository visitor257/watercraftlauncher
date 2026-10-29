# Patch a Forge 1.7.2 universal jar in place so it boots on Java 8.
#
#   powershell -File scripts/lwfix172.ps1 <forge-universal.jar>
#
# Extracts cpw/mods/fml/relauncher/CoreModManager.class, lets lwfix172.mjs retarget
# Collections.sort inside sortTweakList()V to a modCount-safe helper, then writes both
# the patched class and the helper back into the same jar. Idempotent: a marker entry
# (mcver-lwfix172.marker) short-circuits a second run.
param([Parameter(Mandatory=$true)][string]$Jar)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$Jar = (Resolve-Path $Jar).Path
$stage = Join-Path $env:TEMP ("mcver-lwfix-stage-" + [guid]::NewGuid().ToString('N'))

$zip = [System.IO.Compression.ZipFile]::Open($Jar, 'Update')
try {
    if ($zip.GetEntry('mcver-lwfix172.marker')) {
        Write-Host "already patched: $Jar"
        return
    }
    $entry = $zip.GetEntry('cpw/mods/fml/relauncher/CoreModManager.class')
    if (-not $entry) { throw "CoreModManager.class not found in $Jar" }

    $raw = Join-Path $stage 'CoreModManager.class'
    New-Item -ItemType Directory -Force -Path $stage | Out-Null
    $in = $entry.Open()
    try {
        $out = [System.IO.File]::Create($raw)
        try { $in.CopyTo($out) } finally { $out.Dispose() }
    } finally { $in.Dispose() }

    & node (Join-Path $PSScriptRoot 'lwfix172.mjs') $raw $stage
    if ($LASTEXITCODE -ne 0) { throw "lwfix172.mjs failed ($LASTEXITCODE)" }

    foreach ($rel in @('cpw/mods/fml/relauncher/CoreModManager.class',
                       'cpw/mods/fml/relauncher/McverSortFix.class',
                       'mcver-lwfix172.marker')) {
        $existing = $zip.GetEntry($rel)
        if ($existing) { $existing.Delete() }
        $new = $zip.CreateEntry($rel, [System.IO.Compression.CompressionLevel]::Optimal)
        $src = Join-Path $stage ($rel -replace '/', '\')
        $s = [System.IO.File]::OpenRead($src)
        try {
            $d = $new.Open()
            try { $s.CopyTo($d) } finally { $d.Dispose() }
        } finally { $s.Dispose() }
    }
    Write-Host "patched: $Jar"
}
finally {
    $zip.Dispose()
    Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue
}
