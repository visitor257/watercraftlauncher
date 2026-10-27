# 从「屏幕」抓游戏窗口（PrintWindow 对 LWJGL2 的 1.12 窗口会抓到白块，所以这张备用）。
# 用法：powershell -NoProfile -File scripts\capture-screen.ps1 -Out logs\build\gui-x.png
param([string]$Out)

Add-Type -AssemblyName System.Drawing
if (-not ('WShot2' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class WShot2 {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    public struct R { public int L, T, Ri, B; }
}
'@
}

$p = Get-Process java -ErrorAction SilentlyContinue |
    Where-Object { $_.MainWindowTitle -like 'Minecraft*' } |
    Select-Object -First 1
if ($null -eq $p) { '{"ok":false,"reason":"no-window"}'; exit 0 }

$h = [IntPtr]$p.MainWindowHandle
if ($h -eq [IntPtr]::Zero) { '{"ok":false,"reason":"zero-handle"}'; exit 0 }

[void][WShot2]::ShowWindow($h, 9)   # SW_RESTORE
[void][WShot2]::SetForegroundWindow($h)
Start-Sleep -Milliseconds 700

$r = New-Object WShot2+R
if (-not [WShot2]::GetWindowRect($h, [ref]$r)) { '{"ok":false,"reason":"rect-failed"}'; exit 0 }
$w = $r.Ri - $r.L
$hgt = $r.B - $r.T
if ($w -le 0 -or $hgt -le 0) { '{"ok":false,"reason":"bad-size"}'; exit 0 }

$bmp = New-Object System.Drawing.Bitmap $w, $hgt
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.L, $r.T, 0, 0, (New-Object System.Drawing.Size $w, $hgt))
$g.Dispose()
if ($Out) { $bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png) }

$hits = 0
for ($y = 0; $y -lt $hgt; $y += 4) {
    for ($x = 0; $x -lt $w; $x += 4) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -eq 198 -and $c.G -eq 198 -and $c.B -eq 198) { $hits++ }
    }
}
$c0 = $bmp.GetPixel([int]($w / 2), [int]($hgt / 2))
$center = '{0:X2}{1:X2}{2:X2}' -f $c0.R, $c0.G, $c0.B
$bmp.Dispose()

'{{"ok":true,"center":"{0}","hits":{1},"w":{2},"h":{3}}}' -f $center, $hits, $w, $hgt
