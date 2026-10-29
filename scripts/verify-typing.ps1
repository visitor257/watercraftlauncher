# Verify that typing into a launcher text field really works.
#
# Why this exists: the pixel check (verify-gui-one) only proves the panel was
# drawn. It cannot tell whether a text field can take focus, so a screen whose
# input boxes silently swallow every keystroke still "passes". (2026-10-29:
# Forge 1.8.9 ~ 1.12.2 had exactly that bug -- the overlay drew the field and
# forwarded the keyboard, but never forwarded the mouse click, so the field
# never got focus and every character was dropped without an error.)
#
# Two-phase on purpose: legacy (<=1.12.2) game logs are opened exclusively by
# log4j, so they CANNOT be read while the game runs (HANDOVER 6.3). The panel's
# own debug file (mcver-debug.txt) is appended-and-closed, so that one is
# readable live -- it is the "ready" signal. The layout line we need for the
# click only comes from the game log, so:
#   phase A: launch -> wait until the panel is up -> quit -> read layout
#   phase B: launch again -> click the search box -> type -> quit -> read log
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts\verify-typing.ps1 1.12.2-forge-14.23.5.2864
#   ... -Text 17 -Timeout 200
#
# ASCII only on purpose: PowerShell 5.1 parses a BOM-less script as ANSI(GBK),
# and Chinese literals break the parser (HANDOVER 6.4). Chinese markers are
# built from code points instead.
param(
    [Parameter(Mandatory = $true)][string]$Instance,
    [string]$GameDir = 'testgame',
    [int]$Timeout = 200,
    [string]$Text = '17',
    # click the box again before every character -- used to prove that the focus
    # loss comes from the page rebuilding its widget, not from the field refusing
    # input: if "click,type,click,type" ends up as the full string, each click is
    # putting focus back on a freshly created field.
    [switch]$ClickBetween
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public class W {
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
}
"@

# Chinese markers, built from code points so this file stays pure ASCII.
$CAT          = [string]([char]0x5206) + [char]0x7C7B   # fen lei
$SEARCH       = [string]([char]0x641C) + [char]0x7D22   # sou suo
$DOWNLOAD_TAB = [string]([char]0x4E0B) + [char]0x8F7D   # xia zai

$java = 'D:\Program Files\Java\jdk-21.0.7\bin\java.exe'
if (-not (Test-Path $java)) { $java = 'java' }
$cp = Join-Path $root 'tools\probe\out'
if (-not (Test-Path (Join-Path $cp 'dev\mcver\probe\ProbeLaunch.class'))) {
    Write-Host '[typing] FAIL probe missing in tools\probe\out - run scripts\build-probe.ps1 first'
    exit 2
}

$instDir  = Join-Path $GameDir ('versions\' + $Instance)
$debugTxt = Join-Path $instDir 'mcver-debug.txt'
$logDir   = Join-Path $instDir 'logs'

# ---------------------------------------------------------------- helpers ----

function Get-McverGames {
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -match 'minecraft\.launcher\.brand=mcver' }
}

function Stop-Game {
    if ($script:probe -and -not $script:probe.HasExited) {
        try { $script:probe.Kill() } catch { }
    }
    # Kill by command line, not by window title: a freshly launched instance may
    # not have its main window registered yet, and then the process survives and
    # keeps the log file locked (that is exactly how the first attempts hung).
    # Also kill anything java that started with this run: Forge 1.17+ games are
    # launched through BootstrapLauncher and their command line does NOT carry
    # the mcver brand, so the brand filter alone misses them.
    Get-McverGames | ForEach-Object { try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch { } }
    $since = if ($script:started) { $script:started.AddSeconds(-15) } else { (Get-Date).AddMinutes(-30) }
    Get-Process java, javaw -ErrorAction SilentlyContinue |
        Where-Object { $_.StartTime -and $_.StartTime -ge $since } |
        ForEach-Object { try { $_.Kill() } catch { } }
    for ($i = 0; $i -lt 24; $i++) {
        Start-Sleep -Milliseconds 500
        $left = Get-Process java, javaw -ErrorAction SilentlyContinue |
            Where-Object { $_.StartTime -and $_.StartTime -ge $since }
        if (-not $left) { break }
    }
    $script:probe = $null
}

function Start-Game {
    $log = Join-Path $env:TEMP ("mcver-typing-{0}-{1}.log" -f $Instance, (Get-Random))
    # the debug file is appended to across runs; drop it so "ready" can only be
    # triggered by THIS launch
    if (Test-Path $debugTxt) { Remove-Item $debugTxt -Force -ErrorAction SilentlyContinue }
    $env:MCVER_OPEN_GUI = '1'
    $env:MCVER_TAB = $DOWNLOAD_TAB
    $script:started = Get-Date
    $script:probe = Start-Process -FilePath $java -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -ArgumentList @('-cp', $cp, 'dev.mcver.probe.ProbeLaunch', $GameDir, $Instance, "$Timeout", 'ZZNEVERZZ') `
        -WorkingDirectory $root
}

function Read-Debug($path) {
    if (-not (Test-Path $path)) { return '' }
    try { return [System.IO.File]::ReadAllText($path) } catch { return '' }
}

# Panel is up AND the version list finished loading (children jumps from 15 to
# 23 once the download list is built). Everything else waits on this.
function Wait-Ready([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 800
        $txt = Read-Debug $debugTxt
        if ($txt -match 'screen=MAIN') {
            $m = [regex]::Matches($txt, 'children=(\d+)')
            $last = if ($m.Count -gt 0) { [int]$m[$m.Count - 1].Groups[1].Value } else { 0 }
            if ($last -ge 18) { return $true }
        }
    }
    return $false
}

function Read-LogText([string]$path) {
    # may still be held for a moment right after the kill -> retry
    $bytes = $null
    for ($i = 0; $i -lt 30; $i++) {
        try { $bytes = [System.IO.File]::ReadAllBytes($path); break }
        catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $bytes) { throw "cannot read $path" }
    $utf8 = [System.Text.Encoding]::UTF8.GetString($bytes)
    # Decode by validity, not by "does it contain mcver:" -- ASCII markers also
    # appear in GBK logs, and decoding those as UTF-8 turns every Chinese marker
    # into U+FFFD, which is how the first assertions "failed" against a log that
    # actually had the evidence (HANDOVER 6.4).
    if ($utf8.IndexOf([char]0xFFFD) -lt 0) { return $utf8 }
    return [System.Text.Encoding]::GetEncoding(936).GetString($bytes)
}

# Newest game log written since the given time. Only callable after the game
# has exited (legacy logs are exclusively locked while it runs).
function Get-GameLog($since) {
    $c = Get-ChildItem $logDir -Filter 'mcver-launch-*.log' -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -ge $since } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($c) { return $c.FullName }
    return $null
}

function Get-GameWindow {
    # Forge 1.17+ launches through BootstrapLauncher, so look for any java that
    # started with this run and owns a window, not just the mcver-branded ones.
    $since = if ($script:started) { $script:started.AddSeconds(-15) } else { (Get-Date).AddMinutes(-30) }
    foreach ($p in (Get-Process java, javaw -ErrorAction SilentlyContinue |
            Where-Object { $_.StartTime -and $_.StartTime -ge $since })) {
        if ($p.MainWindowHandle -ne [IntPtr]::Zero) { return $p.MainWindowHandle }
    }
    return [IntPtr]::Zero
}

# Grabbing the foreground is what decides whether the game receives the click
# and the keystrokes at all: the game ignores input while its window is not
# active, and nothing in the log says a word about it (2026-10-29: the first
# attempt typed into a window that was never foreground and the mod heard
# nothing at all -- no click, no key).
function Focus-Window([IntPtr]$hwnd) {
    for ($i = 0; $i -lt 12; $i++) {
        [void][W]::ShowWindow($hwnd, 9)   # SW_RESTORE
        [void][W]::SetForegroundWindow($hwnd)
        Start-Sleep -Milliseconds 400
        if ([W]::GetForegroundWindow() -eq $hwnd) {
            Write-Host '[typing] window is foreground'
            return $true
        }
        # Windows only honours a foreground change from a process that recently
        # had user input; a synthetic ALT tap makes this process "the user".
        [W]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 60
        [W]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
    }
    $fg = [W]::GetForegroundWindow()
    Write-Host ("[typing] WARN could not take foreground (fg={0} target={1}) - input will be dropped" -f $fg, $hwnd)
    return $false
}

function Click-Gui([IntPtr]$hwnd, [int]$gx, [int]$gy, [int]$guiW, [int]$guiH) {
    $r = New-Object W+RECT
    [void][W]::GetClientRect($hwnd, [ref]$r)
    $cw = $r.Right - $r.Left
    $ch = $r.Bottom - $r.Top
    $pt = New-Object W+POINT
    $pt.X = [int][Math]::Round($gx * $cw / $guiW)
    $pt.Y = [int][Math]::Round($gy * $ch / $guiH)
    [void][W]::ClientToScreen($hwnd, [ref]$pt)
    [void][W]::SetCursorPos($pt.X, $pt.Y)
    Start-Sleep -Milliseconds 150
    [W]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 70
    [W]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    Write-Host ("[typing] clicked gui({0},{1}) -> screen({2},{3})  client {4}x{5}" -f $gx, $gy, $pt.X, $pt.Y, $cw, $ch)
}

function Save-Shot([string]$tag) {
    Add-Type -AssemblyName System.Drawing -ErrorAction SilentlyContinue
    $g = Get-McverGames | Select-Object -First 1
    if (-not $g) { return }
    $p = Get-Process -Id $g.ProcessId -ErrorAction SilentlyContinue
    if (-not $p -or $p.MainWindowHandle -eq [IntPtr]::Zero) { return }
    $r = New-Object W+RECT
    [void][W]::GetWindowRect($p.MainWindowHandle, [ref]$r)
    $w = $r.Right - $r.Left
    $h = $r.Bottom - $r.Top
    if ($w -le 0 -or $h -le 0) { return }
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $gfx = [System.Drawing.Graphics]::FromImage($bmp)
    $gfx.CopyFromScreen($r.Left, $r.Top, 0, 0, (New-Object System.Drawing.Size($w, $h)))
    $dir = Join-Path $root 'logs\build'
    $file = Join-Path $dir ("typing-{0}-{1}.png" -f $Instance, $tag)
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $gfx.Dispose(); $bmp.Dispose()
    Write-Host ("[typing] shot -> {0}" -f $file)
}

function Send-Keys([string]$s) {
    foreach ($c in $s.ToCharArray()) {
        if ($c -ge '0' -and $c -le '9') { $vk = 0x30 + ([int]$c - 48) }
        elseif ($c -ge 'a' -and $c -le 'z') { $vk = 0x41 + ([int]$c - 97) }
        elseif ($c -ge 'A' -and $c -le 'Z') { $vk = 0x41 + ([int]$c - 65) }
        elseif ($c -eq '.') { $vk = 0xBE }
        else { throw "unsupported char: $c" }
        [W]::keybd_event([byte]$vk, 0, 0, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 55
        [W]::keybd_event([byte]$vk, 0, 2, [UIntPtr]::Zero)
        Start-Sleep -Milliseconds 75
    }
    Write-Host ("[typing] typed '{0}'" -f $s)
}

# ------------------------------------------------------------------ phase A --

Write-Host ("[typing] {0}: phase A (discover layout)" -f $Instance)
Stop-Game
Start-Game
if (-not (Wait-Ready $Timeout)) {
    Write-Host '[typing] FAIL panel never reached screen=MAIN in phase A'
    Stop-Game
    exit 1
}
Stop-Game

$logA = Get-GameLog $script:started
if (-not $logA) { Write-Host '[typing] FAIL phase A produced no game log'; exit 1 }
$txtA = Read-LogText $logA

$gm = [regex]::Match($txtA, 'gui=(\d+)x(\d+)')
$lm = [regex]::Matches($txtA, 'search@(\d+),(\d+),(\d+),(\d+)')
if (-not $gm.Success -or $lm.Count -eq 0) {
    Write-Host '[typing] FAIL no layout line (search@...) or gui=WxH in the log'
    exit 1
}
$guiW = [int]$gm.Groups[1].Value
$guiH = [int]$gm.Groups[2].Value
$L = $lm[$lm.Count - 1]
$sx = [int]$L.Groups[1].Value; $sy = [int]$L.Groups[2].Value
$sw = [int]$L.Groups[3].Value; $sh = [int]$L.Groups[4].Value
Write-Host ("[typing] gui={0}x{1} search@{2},{3} {4}x{5}" -f $guiW, $guiH, $sx, $sy, $sw, $sh)

# ------------------------------------------------------------------ phase B --

Write-Host ("[typing] {0}: phase B (click + type '{1}')" -f $Instance, $Text)
Start-Game
if (-not (Wait-Ready $Timeout)) {
    Write-Host '[typing] FAIL panel never reached screen=MAIN in phase B'
    Stop-Game
    exit 1
}

$hwnd = [IntPtr]::Zero
for ($i = 0; $i -lt 30 -and $hwnd -eq [IntPtr]::Zero; $i++) {
    Start-Sleep -Milliseconds 500
    $hwnd = Get-GameWindow
}
if ($hwnd -eq [IntPtr]::Zero) {
    Write-Host '[typing] FAIL game window not found'
    Stop-Game
    exit 1
}

[void][W]::ShowWindow($hwnd, 9)
[void][W]::SetForegroundWindow($hwnd)
Start-Sleep -Milliseconds 700
[void](Focus-Window $hwnd)
Save-Shot 'before'
if ($ClickBetween) {
    foreach ($ch in $Text.ToCharArray()) {
        Click-Gui $hwnd ($sx + [int]($sw / 2)) ($sy + [int]($sh / 2)) $guiW $guiH
        Start-Sleep -Milliseconds 500
        Send-Keys ([string]$ch)
        Start-Sleep -Milliseconds 700
    }
} else {
    Click-Gui $hwnd ($sx + [int]($sw / 2)) ($sy + [int]($sh / 2)) $guiW $guiH
    Start-Sleep -Milliseconds 700
    Save-Shot 'clicked'
    Send-Keys $Text
}
Start-Sleep -Seconds 2
Save-Shot 'typed'
Start-Sleep -Seconds 2
Stop-Game

$logB = Get-GameLog $script:started
if (-not $logB) { Write-Host '[typing] FAIL phase B produced no game log'; exit 1 }
$txtB = Read-LogText $logB
$hit = ($txtB -split "`r?`n") | Where-Object { $_ -match ($SEARCH + '=' + [regex]::Escape($Text) + '( |$)') } | Select-Object -First 1

if ($hit) {
    Write-Host ("[typing] PASS search box received '{0}'" -f $Text)
    Write-Host ('    ' + $hit.Trim())
    exit 0
}
Write-Host ("[typing] FAIL clicked the box and typed '{0}', but no search log line changed" -f $Text)
Write-Host '[typing] ---- last 12 log lines ----'
($txtB -split "`r?`n") | Select-Object -Last 12 | ForEach-Object { Write-Host ('    ' + $_) }
exit 1
