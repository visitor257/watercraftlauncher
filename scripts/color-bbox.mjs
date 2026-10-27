// 打印指定颜色的包围盒，用来反推「我们画的坐标」和「屏幕像素」的映射关系。
import { execFileSync } from 'node:child_process';

const png = process.argv[2];
const ps = `
Add-Type -AssemblyName System.Drawing
$b = [System.Drawing.Bitmap]::FromFile('${png}')
$targets = @{ 'C6C6C6' = @(198,198,198); 'FFFFFF' = @(255,255,255); '000000' = @(0,0,0); 'FF00FF' = @(255,0,255) }
foreach ($k in $targets.Keys) {
  $t = $targets[$k]
  $minX = 999999; $minY = 999999; $maxX = -1; $maxY = -1; $n = 0
  for ($y = 0; $y -lt $b.Height; $y += 2) {
    for ($x = 0; $x -lt $b.Width; $x += 2) {
      $c = $b.GetPixel($x, $y)
      if ($c.R -eq $t[0] -and $c.G -eq $t[1] -and $c.B -eq $t[2]) {
        $n++
        if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
        if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
      }
    }
  }
  if ($n -gt 0) { "$k n=$n bbox=($minX,$minY)-($maxX,$maxY)" } else { "$k none" }
}
"image=$($b.Width)x$($b.Height)"
$b.Dispose()
`;
console.log(execFileSync('powershell', ['-NoProfile', '-Command', ps], { encoding: 'utf8', maxBuffer: 1 << 22 }));
