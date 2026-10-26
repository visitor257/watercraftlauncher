// 把 build-all.ps1 的 1.21.x 五个 target 合并成一个「1.21 通用」target。
//
// 之前是 1.21.4 / 1.21.11 / 1.21.10 / 1.21.9 / 1.21.1 各出一个 jar。
// 现在源码改成版本无关（右键轮询 + registryKey 反射），一个 jar 盖 1.21 ~ 1.21.11。
//
// 编译基准取系列里最老的 1.21：只能用 1.21 就有的 API，新版本才加的一律反射。
// 依赖声明写 >=1.21 <1.22（由 build.gradle 的 -Pwide_mc 算，不在命令行传 `>`）。
import fs from 'node:fs';

const p = 'D:/Development/watercraftlauncher/scripts/build-all.ps1';
let s = fs.readFileSync(p, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const lines = raw.split('\n');
const keep = [];
let removed = 0;
for (const line of lines) {
  const isOld121 =
    /@\{\s*name = '1\.21(\.\d+)?'/.test(line) && /compat = '(1\.21\.1|1\.21\.9|)'/.test(line);
  if (isOld121) { removed++; continue; }
  keep.push(line);
}

// 新的 1.21 通用 target：compat 留空（不需要覆盖层），wide = '1.21'
const newLine = "    @{ name = '1.21';    mc = '1.21';    yarn = '1.21+build.9';     api = '0.102.0+1.21';   compat = '';       java = '21'; wide = '1.21' },";
const anchor = keep.findIndex(l => l.trim().startsWith('@{') && l.includes("'1.20.1'"));
if (anchor < 0) { console.log('MISS: 找不到 1.20.1 那行）；'); process.exit(1); }
keep.splice(anchor, 0, newLine);

let out = keep.join('\n');

// 传参：wide 非空时加 -Pwide_mc
const oldArgs = `    if ($t.compat -ne '') { $gargs += "-Pcompat=$($t.compat)" }`;
const newArgs = `    if ($t.compat -ne '') { $gargs += "-Pcompat=$($t.compat)" }
    # 一个 jar 盖一整个大版本：让 fabric.mod.json 写 >=1.21 <1.22（由 build.gradle 算，不传 > 符号）
    if ($t.wide -ne '' -and $t.wide) { $gargs += "-Pwide_mc=$($t.wide)" }`;
if (!out.includes(oldArgs)) { console.log('MISS: 传参锚点'); process.exit(1); }
out = out.replace(oldArgs, newArgs);

if (crlf) out = out.replace(/\n/g, '\r\n');
fs.writeFileSync(p, Buffer.from(out, 'utf8'));
console.log(`删掉 ${removed} 个 1.21.x target，加入 1 个 1.21 通用 target`);
