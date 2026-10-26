// 把 1.21.11 加进界面验证脚本（verify-gui.ps1）。
// 三处：默认 Targets、实例目录（用安装器装的，叫 v12111）、fabric-api 版本表。
// 注意：这个 .ps1 带 BOM，必须用 node 的 utf8 读写保留 \uFEFF，用 PowerShell 的
// WriteAllText 会丢 BOM → PS 5.1 按 ANSI 读 → 中文注释全乱码 + 语法错误（踩过）。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/verify-gui.ps1';
let s = fs.readFileSync(p, 'utf8');
const nl = s.includes('\r\n') ? '\r\n' : '\n';

function sub(from, to) {
  if (s.includes(to)) return 'already';
  if (!s.includes(from)) throw new Error('MISS: ' + from.slice(0, 60));
  s = s.replace(from, to);
  return 'ok';
}

sub(
  `$Targets = @('1.21.4', '1.21.1', '1.20.1', '1.19.4', '1.18.2', '1.17.1', '1.16.5')`,
  `$Targets = @('1.21.11', '1.21.4', '1.21.1', '1.20.1', '1.19.4', '1.18.2', '1.17.1', '1.16.5')`
);

sub(
  `    '1.17.1' = 'fabric-loader-0.20.1-1.17.1'`,
  `    '1.17.1' = 'fabric-loader-0.20.1-1.17.1'` + nl + `    '1.21.11' = 'v12111'`
);

sub(
  `    '1.21.4' = @{ v = '0.119.4+1.21.4'; url = '' }`,
  `    '1.21.11' = @{ v = '0.141.6+1.21.11'; url = '' }` + nl + `    '1.21.4' = @{ v = '0.119.4+1.21.4'; url = '' }`
);

fs.writeFileSync(p, Buffer.from(s, 'utf8'));
console.log('verify-gui.ps1 已更新');
