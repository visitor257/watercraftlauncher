// 换机器第一件事：把所有脚本里写死的 JDK / JRE 绝对路径改成这台机器的真实路径。
//
//   node scripts/portable-jdk.mjs            # 只检查（默认，不写文件）
//   node scripts/portable-jdk.mjs --write    # 真的改
//
// 为什么需要：scripts/ 里有二十来处 `const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin'`
// 这种写死的行（还有用 C:/ 的，甚至同一台机器上两个盘符混用）。在别的电脑上这些路径不存在，
// 构建脚本会以「找不到 javac」或 `spawn ENOENT` 失败，而错误信息看不出是路径问题。
//
// 解析顺序（都要求 --version 能跑通，不是只判断目录存在）：
//   JDK 21 : $env:MCVER_JDK21 > $env:JAVA_HOME > 常见安装目录里版本最高的 jdk-2x
//   JRE 8  : $env:MCVER_JRE8  > 常见安装目录里的 jre-1.8 / jdk-1.8
// Java 8 只在「老版本启动验收」时用到（1.7.2 ~ 1.12.2 那几代的运行时），没有也能构建。
import { spawnSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const WRITE = process.argv.includes('--write');

const JAVA_ROOTS = (() => {
  const roots = [];
  // 先把本机所有盘符的 Program Files 都扫上（E 盘装 Java 很常见）。
  for (const d of 'CDEFGH'.split('')) {
    roots.push(`${d}:/Program Files/Java`, `${d}:/Program Files/Eclipse Adoptium`,
      `${d}:/Program Files/Microsoft`, `${d}:/Program Files/Zulu`,
      `${d}:/Program Files (x86)/Java`);
  }
  roots.push(join(process.env.LOCALAPPDATA || '', 'Programs', 'Eclipse Adoptium'));
  return roots;
})();

function versionOf(exe) {
  // 必须用 spawnSync：`java -version` 把版本打到 stderr 但退出码是 0，
  // execFileSync 只会返回空的 stdout（不抛错），于是每个 JRE 都被当成「版本读不到」。（踩过）
  const r = spawnSync(exe, ['-version'], { encoding: 'utf8' });
  const s = String(r.stdout || '') + String(r.stderr || '');
  return /version "/.test(s) || /javac \d/.test(s) ? s : null;
}

/** 从常见安装目录里挑主版本 >= min 的 JDK/JRE，取版本号最大的那个。 */
function discover(prefixes, minMajor, needJavac) {
  const hits = [];
  for (const root of JAVA_ROOTS) {
    if (!root || !existsSync(root)) continue;
    for (const name of readdirSync(root)) {
      if (!prefixes.some((p) => name.toLowerCase().startsWith(p))) continue;
      const home = join(root, name);
      const exe = join(home, 'bin', needJavac ? 'javac.exe' : 'java.exe');
      if (!existsSync(exe)) continue;
      const v = versionOf(exe);
      if (!v) continue;
      const m = /version "(\d+)(?:\.(\d+))?/.exec(v);
      if (!m) continue;
      const major = Number(m[1]) === 1 ? Number(m[2]) : Number(m[1]);
      if (major < minMajor) continue;
      hits.push({ home, major, raw: v.split(/\r?\n/)[0].trim() });
    }
  }
  hits.sort((a, b) => b.major - a.major);
  return hits[0] || null;
}

const jdk = (() => {
  for (const cand of [process.env.MCVER_JDK21, process.env.JAVA_HOME]) {
    if (!cand) continue;
    const exe = join(cand, 'bin', 'javac.exe');
    if (existsSync(exe) && versionOf(exe)) return { home: cand, raw: versionOf(exe).split(/\r?\n/)[0].trim() };
  }
  return discover(['jdk-2', 'jdk2', 'jdk-17', 'jdk17'], 17, true);
})();

const jre8 = (() => {
  if (process.env.MCVER_JRE8 && existsSync(join(process.env.MCVER_JRE8, 'bin', 'java.exe'))) {
    return { home: process.env.MCVER_JRE8 };
  }
  return discover(['jre-1.8', 'jre1.8', 'jre8', 'jdk-1.8', 'jdk1.8', 'jdk8'], 8, false);
})();

console.log('JDK : ' + (jdk ? jdk.home + '   ' + (jdk.raw || '') : '找不到（需要 JDK 17+，推荐 21）'));
console.log('JRE8: ' + (jre8 ? jre8.home : '找不到（只影响 1.7~1.12 老版本启动验收）'));
if (!jdk) {
  console.error('\n没有可用 JDK，先装 JDK 21，或用 MCVER_JDK21 指定。');
  process.exit(1);
}

// 匹配「盘符开头、到 jdk-*/jre-* 目录名为止」的那一段，保留后面的 \bin\javac.exe 等后缀。
const PAT = /[A-Za-z]:(?:\\\\|\\|\/)(?:[^'"`\\/\r\n]+(?:\\\\|\\|\/))*?(?:jdk-\d[^'"`\\/\r\n]*|jre-1\.8[^'"`\\/\r\n]*)/g;

const targets = [];
for (const f of readdirSync(join(ROOT, 'scripts'))) {
  if (!/\.(mjs|ps1)$/.test(f)) continue;
  targets.push(join(ROOT, 'scripts', f));
}

let changed = 0;
for (const file of targets) {
  const text = readFileSync(file, 'utf8');
  const hits = [...text.matchAll(PAT)];
  if (!hits.length) continue;

  const seen = new Set();
  for (const h of hits) {
    const lit = h[0];
    const want = /jre-1\.8/i.test(lit) ? jre8 : jdk;
    if (!want) { console.log(`  ?  ${file}: ${lit}（本机没有对应的 Java，跳过）`); continue; }
    if (resolve(lit) === resolve(want.home)) continue;
    seen.add(`${lit}  ->  ${want.home}`);
  }
  if (!seen.size) continue;

  console.log(`\n${file}`);
  for (const s of seen) console.log('  ' + s);
  changed++;

  if (WRITE) {
    const out = text.replace(PAT, (lit) => {
      const want = /jre-1\.8/i.test(lit) ? jre8 : jdk;
      if (!want) return lit;
      // 跟随原文风格：原文用正斜杠就还正斜杠
      return /[A-Za-z]:\//.test(lit) ? want.home.replace(/\\/g, '/') : want.home.replace(/\//g, '\\');
    });
    writeFileSync(file, out, 'utf8');
  }
}

if (!changed) console.log('\n所有脚本里的 Java 路径都是这台机器的，无需改动。');
else if (!WRITE) console.log('\n以上是需要改的（加 --write 自动改）。');
else console.log('\n已写好。跑一次 node scripts/rebuild-all.mjs 验证。');
