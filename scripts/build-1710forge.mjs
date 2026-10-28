// Forge 1.7.10（Forge 10.13.4.1614）构建：
//
//   mk-mojmap-src 生成源码  →  文本替换（Minecraft 撞名 → bridge）
//   →  盖 shim1710（1.14 时代类名的垫片）+ forge1710（overlay）
//   →  javac --release 8  →  合资源（mcmod.info + textures/items）→ jar
//
// 跟 build-1122forge.mjs 只差常量与垫片目录；SRG 映射来自 mk-srg-1710.mjs
// （1.7.10 的 joined.srg 是 v0 文本格式，跟 1.12.2 的 tsrg 不是一回事）。
//
// 用法： node scripts/build-1710forge.mjs
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const MODX = join(ROOT, 'modx');
const MC = '1.7.10';
const FORGE = '10.13.4.1614';
const INSTANCE = `${MC}-Forge${FORGE}-${MC}`;
const W = join(MODX, 'build', `forge-${MC}`);
const JAVA_HOME = process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7';
const javac = join(JAVA_HOME, 'bin', 'javac.exe');
const jarExe = join(JAVA_HOME, 'bin', 'jar.exe');

const srgJar = join(MODX, 'srg', MC, `client-${MC}-srg.jar`);
const srgForgeJar = join(MODX, 'srg', MC, `forge-${FORGE}-srg.jar`);
for (const p of [srgJar, srgForgeJar]) {
  if (!existsSync(p)) throw new Error('缺 ' + p + '\n先跑: node scripts/mk-srg-1710.mjs');
}

// classpath：SRG 原版 + SRG Forge + 实例 json 里的库（natives 跳过）
function libsOf(verId) {
  const json = JSON.parse(readFileSync(join(GAME, 'versions', verId, verId + '.json'), 'utf8'));
  const out = [];
  for (const lib of json.libraries || []) {
    const [group, artifact, version, cls] = lib.name.split(':');
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = `${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}${cls ? '-' + cls : ''}.jar`;
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out;
}

const cp = [srgJar, srgForgeJar, ...libsOf(INSTANCE)].join(';');

rmSync(W, { recursive: true, force: true });
mkdirSync(join(W, 'classes'), { recursive: true });
const src = join(W, 'src');
mkdirSync(src, { recursive: true });

// 1) 共享源码的 yarn → mojmap 机械转换
execFileSync(process.execPath, [join(ROOT, 'scripts', 'mk-mojmap-src.mjs'), src, join(ROOT, 'mod', 'src', 'main', 'java')], { stdio: 'inherit' });

// 2) 源码级改写：net.minecraft.client.Minecraft 在 1.7.10 里本来就有 → 换门面。
//    **必须早于盖 overlay**（否则门面文件自己也被换掉，自我引用）。
const REPLACE = [['net.minecraft.client.Minecraft', 'dev.mcver.bridge.Minecraft']];
let replaced = 0;
(function walk(dir) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    if (e.isDirectory()) { walk(p); continue; }
    if (!p.endsWith('.java')) continue;
    let s = readFileSync(p, 'utf8');
    const before = s;
    for (const [from, to] of REPLACE) s = s.split(from).join(to);
    if (s !== before) { writeFileSync(p, s, 'utf8'); replaced++; }
  }
})(src);
console.log(`  srcReplace: ${replaced} files`);

// 3) 垫片 + overlay（同路径同名 = 后面的赢）
for (const ov of ['shim1710/java', 'forge1710/java']) {
  cpSync(join(MODX, ov), src, { recursive: true, force: true });
}

// 4) 编译
const sources = [];
(function walk(dir) {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (p.endsWith('.java')) sources.push(p);
  }
})(src);

console.log(`  javac ${sources.length} files (--release 8, SRG namespace)`);
execFileSync(javac, [
  '-J-Duser.language=en', '-J-Duser.country=US',
  '--release', '8',
  '-nowarn',
  '-encoding', 'UTF-8',
  '-cp', cp,
  '-d', join(W, 'classes'),
  ...sources,
], { stdio: 'inherit', maxBuffer: 1 << 28 });

// 5) 资源
cpSync(join(MODX, 'forge1710', 'resources'), join(W, 'classes'), { recursive: true, force: true });
for (const junk of ['fabric.mod.json', 'assets/mcver/items']) {
  const p = join(W, 'classes', junk);
  if (existsSync(p)) rmSync(p, { recursive: true, force: true });
}

const outDir = join(MODX, 'out', 'forge-7.10');
mkdirSync(outDir, { recursive: true });
const outJar = join(outDir, `watercraftlauncher-forge-1.0.0-mc${MC}.jar`);
if (existsSync(outJar)) unlinkSync(outJar);
execFileSync(jarExe, ['cf', outJar, '-C', join(W, 'classes'), '.'], { stdio: 'inherit' });
console.log('OK ' + outJar);
