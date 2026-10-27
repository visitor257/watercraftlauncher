# 抓 Minecraft 窗口并数面板色 —— 只做一件事，供 scripts/verify-gui-one.mjs 调用。
#
# 为什么不在这里做流程控制：PowerShell 5.1 上 `$null` 方法调用、`-eq 0` 与空串比较这些坑
# 一个接一个踩（已经踩了两轮），流程放 Node 里写，这里只留「抓图 + 数像素」这一小段。
#
# 输出：一行 JSON。{ok, center, hits, w, h, png} 或 {ok:false, reason}
param([string]$Out = '')

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if (-not ('WShot' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public class WShot {
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint f);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out R r);
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

$r = New-Object WShot+R
if (-not [WShot]::GetWindowRect($h, [ref]$r)) { '{"ok":false,"reason":"rect-failed"}'; exit 0 }
$w = $r.Ri - $r.L
$hgt = $r.B - $r.T
if ($w -le 0 -or $hgt -le 0) { '{"ok":false,"reason":"bad-size"}'; exit 0 }

$bmp = New-Object System.Drawing.Bitmap $w, $hgt
$g = [System.Drawing.Graphics]::FromImage($bmp)
$dc = $g.GetHdc()
[void][WShot]::PrintWindow($h, $dc, 2)
$g.ReleaseHdc($dc)
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
