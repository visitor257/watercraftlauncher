// 1.8 ~ 1.12 那一代（SRG 命名空间）的通用构建：一个脚本覆盖 1.8.9 / 1.9.4 / 1.10.2 / 1.11.2 / 1.12.2。
//
//   mk-mojmap-src（yarn → mojmap）
//     → 文本替换（net.minecraft.client.Minecraft 撞名 → dev.mcver.bridge.Minecraft）
//     → 盖 shim<ver>（1.14 时代类名的垫片）+ 若干个 overlay（后面的赢）
//     → javac --release 8（classpath = SRG 原版 + SRG Forge + 实例库）
//     → 合资源（每个 overlay 目录里的 resources/ 依次覆盖）
//     → jar
//
// 映射由 scripts/mk-srg-srgv0.mjs 生成（这一代的 srg 是 joined.srg v0 文本格式）。
//
// 用法：
//   node scripts/build-srg-legacy.mjs --mc 1.11.2 --forge 13.20.1.2588 \
//        --instance 1.11.2-forge1.11.2-13.20.1.2588 --shim shim1122 --overlay forge102,forge111
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const MODX = join(ROOT, 'modx');
const JAVA_HOME = process.env.JAVA_HOME || 'D:/Program Files/Java/jdk-21.0.7';
const javac = join(JAVA_HOME, 'bin', 'javac.exe');
const jarExe = join(JAVA_HOME, 'bin', 'jar.exe');

function arg(name, dflt) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}

const MC = arg('mc', '1.11.2');
const FORGE = arg('forge', '13.20.1.2588');
const INSTANCE = arg('instance', `${MC}-forge${MC}-${FORGE}`);
const SHIM = arg('shim', 'shim1122');
const OVERLAYS = arg('overlay', 'forge102').split(',').filter(Boolean);
const SHORT = arg('short', MC.split('.').slice(0, 2).join('.'));
const JAR_NAME = arg('jar', `watercraftlauncher-forge-1.0.0-mc${MC}`);

const srgJar = join(MODX, 'srg', MC, `client-${MC}-srg.jar`);
const srgForgeJar = join(MODX, 'srg', MC, `forge-${FORGE}-srg.jar`);
for (const p of [srgJar, srgForgeJar]) {
  if (!existsSync(p)) throw new Error('缺 ' + p + '\n先跑: node scripts/mk-srg-srgv0.mjs --mc ' + MC + ' --instance ' + INSTANCE + ' --forge ' + FORGE);
}

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

const W = join(MODX, 'build', `forge-${MC}`);
rmSync(W, { recursive: true, force: true });
mkdirSync(join(W, 'classes'), { recursive: true });
const src = join(W, 'src');
mkdirSync(src, { recursive: true });

// 1) 共享源码的 yarn → mojmap 机械转换
execFileSync(process.execPath, [join(ROOT, 'scripts', 'mk-mojmap-src.mjs'), src, join(ROOT, 'mod', 'src', 'main', 'java')], { stdio: 'inherit' });

// 2) 源码级改写（必须在盖 overlay 之前，否则门面文件自己也被换掉）
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
for (const ov of [SHIM + '/java', ...OVERLAYS.map((o) => o + '/java')]) {
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

// 5) 资源：每个 overlay 目录里的 resources/ 依次覆盖
for (const ov of [...OVERLAYS]) {
  const r = join(MODX, ov, 'resources');
  if (existsSync(r)) cpSync(r, join(W, 'classes'), { recursive: true, force: true });
}
for (const junk of ['fabric.mod.json', 'assets/mcver/items']) {
  const p = join(W, 'classes', junk);
  if (existsSync(p)) rmSync(p, { recursive: true, force: true });
}

const outDir = join(MODX, 'out', `forge-${SHORT}`);
mkdirSync(outDir, { recursive: true });
const outJar = join(outDir, JAR_NAME + '.jar');
if (existsSync(outJar)) unlinkSync(outJar);
execFileSync(jarExe, ['cf', outJar, '-C', join(W, 'classes'), '.'], { stdio: 'inherit' });
console.log('OK ' + outJar);
