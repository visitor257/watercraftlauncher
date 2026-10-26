// 1.19 系列拆成两个 jar（真正的 API 断层，不是偷懒）：
//   mc1.19   → 1.19 / 1.19.1 / 1.19.2   编译基准 1.19.2（旧注册表 + FabricItemGroupBuilder v0）
//   mc1.19.3 → 1.19.3 / 1.19.4          编译基准 1.19.4（新注册表 + ItemGroupEvents v1）
// 同时给 build-all.ps1 加 wide_max 支持（上界 1.19.3）。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/build-all.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const oldRow = `    @{ name = '1.19'; mc = '1.19.4';  yarn = '1.19.4+build.2';  api = '0.87.2+1.19.4';   compat = '1.19';  java = '17'; wide = '1.19' },`;
const newRows = `    # 1.19 系列内部有真断层（Registries 字段类型 + 渲染签名 + 创造栅 API），拆两个 jar
    @{ name = '1.19'; mc = '1.19.2';  yarn = '1.19.2+build.10'; api = '0.77.0+1.19.2';   compat = '1.19';  java = '17'; wide = '1.19'; wideMax = '1.19.3' },
    @{ name = '1.19.3'; mc = '1.19.4'; yarn = '1.19.4+build.2'; api = '0.87.2+1.19.4';  compat = '1.19.3'; java = '17'; wide = '1.19.3' },`;

if (!raw.includes(oldRow)) { console.log('MISS: 1.19 行'); process.exit(1); }
raw = raw.replace(oldRow, newRows);

// 传参：wideMax
const argAnchor = `    if ($t.wide -ne '' -and $t.wide) { $gargs += "-Pwide_mc=$($t.wide)" }`;
const argNew = `    if ($t.wide -ne '' -and $t.wide) { $gargs += "-Pwide_mc=$($t.wide)" }
    if ($t.wideMax) { $gargs += "-Pwide_max=$($t.wideMax)" }`;
if (!raw.includes(argNew)) {
  if (!raw.includes(argAnchor)) { console.log('MISS: 传参'); process.exit(1); }
  raw = raw.replace(argAnchor, argNew);
}

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(raw, 'utf8'));
console.log('build-all.ps1 已加 1.19 / 1.19.3 两个 target');
