// 把 PNG 降采样成 ASCII 色块图，用来看布局（我看不了图，只能这样读）。
import { execFileSync } from 'node:child_process';

const png = process.argv[2];
const cols = Number(process.argv[3] || 60);
const rows = Number(process.argv[4] || 28);

const ps = `
Add-Type -AssemblyName System.Drawing
$b = [System.Drawing.Bitmap]::FromFile('${png}')
$cols = ${cols}; $rows = ${rows}
$sb = New-Object System.Text.StringBuilder
for ($ry = 0; $ry -lt $rows; $ry++) {
  for ($rx = 0; $rx -lt $cols; $rx++) {
    $x = [int]($b.Width * ($rx + 0.5) / $cols); $y = [int]($b.Height * ($ry + 0.5) / $rows)
    $c = $b.GetPixel($x, $y)
    $ch = '.'
    if ($c.R -eq 198 -and $c.G -eq 198 -and $c.B -eq 198) { $ch = 'P' }
    elseif ($c.R -gt 240 -and $c.G -gt 240 -and $c.B -gt 240) { $ch = 'W' }
    elseif ($c.R -lt 40 -and $c.G -lt 40 -and $c.B -lt 40) { $ch = '#' }
    elseif ([Math]::Abs($c.R - $c.G) -lt 12 -and [Math]::Abs($c.G - $c.B) -lt 12) { $ch = '-' }
    else { $ch = '+' }
    [void]$sb.Append($ch)
  }
  [void]$sb.AppendLine()
}
$sb.ToString()
$b.Dispose()
`;
console.log(execFileSync('powershell', ['-NoProfile', '-Command', ps], { encoding: 'utf8', maxBuffer: 1 << 20 }));
