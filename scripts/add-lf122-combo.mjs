// 把「1.12.2 + FABRIC（Legacy Fabric）」加进验证矩阵。
// 注意：这个 .ps1 带 BOM，PowerShell 5.1 靠它才知道是 UTF-8；用 PowerShell 的
// WriteAllText 会丢掉 BOM，整个文件的中文就变乱码、脚本直接语法错误（踩过）。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-matrix.ps1';
let s = fs.readFileSync(p, 'utf8');   // 'utf8' 会把 BOM 解成 \uFEFF，写回时保留
if (!s.startsWith('\uFEFF')) console.log('警告：文件没有 BOM');

const nl = s.includes('\r\n') ? '\r\n' : '\n';
const anchor = `    @{ mc='1.12.2'; kind='FORGE'; shader='NONE'; phase=3; note='已过 / Java 8' },`;
const row = `    @{ mc='1.12.2'; kind='FABRIC'; shader='NONE'; phase=3; note='Legacy Fabric' },`;

if (s.includes(row)) {
  console.log('已经加过了');
} else if (!s.includes(anchor)) {
  console.log('MISS: 找不到 1.12.2 Forge 那行');
  process.exit(1);
} else {
  s = s.replace(anchor, anchor + nl + row);
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('已插入 Legacy Fabric 组合');
}
