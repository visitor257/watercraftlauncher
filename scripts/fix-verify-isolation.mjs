// verify-gui.ps1：跑之前确保实例开了版本隔离。
// 不这样做的后果（踩过）：ProbeInstall 新装出来的实例没有 .mcver-isolated，
// 日志落到 testgame\logs、options.txt 也不生效 → 表现为「没找到本轮日志」，
// 让人以为启动失败，其实是找错了地方。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const anchor = `    Ensure-Mods $id $v`;
const add = `    # 实例必须开着版本隔离，否则日志/options.txt 都不在这个目录里
    $marker = Join-Path $testgame "versions\\$id\\.mcver-isolated"
    if (-not (Test-Path $marker)) { New-Item -ItemType File -Force -Path $marker | Out-Null }

    Ensure-Mods $id $v`;

if (raw.includes('.mcver-isolated')) {
  console.log('已经改过');
} else {
  if (!raw.includes(anchor)) { console.log('MISS'); process.exit(1); }
  raw = raw.replace(anchor, add);
  if (crlf) raw = raw.replace(/\n/g, '\r\n');
  fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
  console.log('verify-gui.ps1 现在会自动补版本隔离标记');
}
