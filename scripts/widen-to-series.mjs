// 把「一个补丁版本一个 jar」改成「一个 1.xx 系列一个 jar」。
//
//   compat 目录  1.16.5 → 1.16, 1.17.1 → 1.17, 1.18.2 → 1.18, 1.19.4 → 1.19, 1.20.1 → 1.20
//   dist 产物    mc1.16.5 → mc1.16 ...
//
// 编译基准的选择原则：**取该系列里「不抬高 Java 要求」的最新补丁版**。
//   1.21 → 1.21.11（Java 21）
//   1.20 → 1.20.4 （Java 17；1.20.5/1.20.6 要 Java 21，拿来当基准会把整个 jar 抬到 21，
//                   1.20.1 的用户就装不上了 —— 那两个版本靠 try/catch 退）
//   1.19 → 1.19.4 / 1.18 → 1.18.2 / 1.17 → 1.17.1 / 1.16 → 1.16.5（都已经是该系列最新）
import fs from 'node:fs';
import path from 'node:path';

const root = 'D:/Development/watercraftlauncher';
const compatRoot = path.join(root, 'mod/src/compat');

// ---- 1) 重命名 compat 目录
const renames = [
  ['1.16.5', '1.16'],
  ['1.17.1', '1.17'],
  ['1.18.2', '1.18'],
  ['1.19.4', '1.19'],
  ['1.20.1', '1.20'],
];
for (const [from, to] of renames) {
  const a = path.join(compatRoot, from);
  const b = path.join(compatRoot, to);
  if (fs.existsSync(a) && !fs.existsSync(b)) {
    fs.renameSync(a, b);
    console.log(`compat ${from} -> ${to}`);
  } else if (fs.existsSync(b)) {
    console.log(`compat ${to} 已存在，跳过`);
  }
}

// ---- 2) 重写 build-all.ps1 的 target 表
const ps1 = path.join(root, 'scripts/build-all.ps1');
let s = fs.readFileSync(ps1, 'utf8');
const crlf = s.includes('\r\n');
let raw = crlf ? s.replace(/\r\n/g, '\n') : s;

const table = `$targets = @(
    # 一个 jar 盖一整个 1.xx 系列。编译基准 = 该系列里「不抬高 Java 要求」的最新补丁版：
    #   1.20.5/1.20.6 要 Java 21，不能当 1.20 的基准（会把 1.20.1 的用户挡在门外），
    #   它们靠 NoSuchMethodError / 静态分支退回；1.21 反过来必须站在最新的 1.21.11 上
    #   （Item.Settings#registryKey 是 1.21.2 才有的）。
    @{ name = '1.21'; mc = '1.21.11'; yarn = '1.21.11+build.6'; api = '0.141.6+1.21.11'; compat = '';    java = '21'; wide = '1.21' },
    @{ name = '1.20'; mc = '1.20.4';  yarn = '1.20.4+build.3';  api = '0.97.1+1.20.4';   compat = '1.20';  java = '17'; wide = '1.20' },
    @{ name = '1.19'; mc = '1.19.4';  yarn = '1.19.4+build.2';  api = '0.87.2+1.19.4';   compat = '1.19';  java = '17'; wide = '1.19' },
    @{ name = '1.18'; mc = '1.18.2';  yarn = '1.18.2+build.4';  api = '0.77.0+1.18.2';   compat = '1.18';  java = '17'; wide = '1.18' },
    @{ name = '1.17'; mc = '1.17.1';  yarn = '1.17.1+build.65'; api = '0.46.1+1.17';     compat = '1.17';  java = '16'; wide = '1.17' },
    @{ name = '1.16'; mc = '1.16.5';  yarn = '1.16.5+build.10'; api = '0.42.0+1.16';     compat = '1.16';  java = '8';  wide = '1.16' }
)`;

const start = raw.indexOf('$targets = @(');
const endMark = '\n)\n';
const end = raw.indexOf(endMark, start);
if (start < 0 || end < 0) { console.log('MISS: target 表'); process.exit(1); }
raw = raw.slice(0, start) + table + raw.slice(end + endMark.length - 1);

if (crlf) raw = raw.replace(/\n/g, '\r\n');
fs.writeFileSync(ps1, Buffer.from(raw, 'utf8'));
console.log('build-all.ps1 target 表已改成系列粒度');
