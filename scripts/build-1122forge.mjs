// Forge 1.12.2（Forge 14.23.5.2864）构建：
//
//   mk-mojmap-src 生成源码  →  盖 shim1122（1.14 时代类名的垫片）+ forge1122（overlay）
//   →  文本替换（Minecraft 撞名 → bridge）  →  javac --release 8
//   →  合资源（mcmod.info + 1.12.2 的 textures/items 布局）  →  jar
//
// 和 1.16.5+ 那套（build-neoforge.mjs）的区别：
//   * 编译命名空间是 **SRG**（`func_xxxxx`），不需要 reobf —— 字节码引用就是运行期名字；
//   * classpath 用 `mk-mcp-srg.mjs` 产出的 SRG 版原版 jar + SRG 版 Forge jar。
//
// 用法： node scripts/build-1122forge.mjs
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, unlinkSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const MODX = join(ROOT, 'modx');
const W = join(MODX, 'build', 'forge-1.12.2');
const JAVA_HOME = process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7';
const javac = join(JAVA_HOME, 'bin', 'javac.exe');
const jarExe = join(JAVA_HOME, 'bin', 'jar.exe');

const MC = '1.12.2';
const FORGE = '14.23.5.2864';
const srgJar = join(MODX, 'srg', MC, `client-${MC}-srg.jar`);
const srgForgeJar = join(MODX, 'srg', MC, `forge-${FORGE}-srg.jar`);
for (const p of [srgJar, srgForgeJar]) {
  if (!existsSync(p)) throw new Error('缺 ' + p + '\n先跑: node scripts/mk-mcp-srg.mjs --forge ' + FORGE);
}

// classpath：SRG 原版 + SRG Forge + 实例 json 里的所有库（natives 跳过）
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

const cp = [srgJar, srgForgeJar, ...libsOf(MC), ...libsOf(`${MC}-forge-${FORGE}`)].join(';');

rmSync(W, { recursive: true, force: true });
mkdirSync(join(W, 'classes'), { recursive: true });
const src = join(W, 'src');
mkdirSync(src, { recursive: true });

// 1) 共享源码的 yarn → mojmap 机械转换
execFileSync(process.execPath, [join(ROOT, 'scripts', 'mk-mojmap-src.mjs'), src, join(ROOT, 'mod', 'src', 'main', 'java')], { stdio: 'inherit' });



// 3) 源码级改写
//
// `net.minecraft.client.Minecraft` 在 1.12.2 里**已经存在**（原版就有），往 mod jar 里再放
// 一份同名垫片会跟原版撞车，而且 LaunchClassLoader 的 URL 顺序不保证谁赢 —— 那种 bug
// 只在某些启动路径上出现，最难查。所以换成 dev.mcver.bridge.Minecraft 门面，
// 这里把 import 里的全限定名整串替换掉（简单名 `Minecraft` 不变，页面代码零改动）。
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

// 2) 垫片 + overlay（同路径同名 = 后面的赢）。
//    **必须在替换之后**：overlay / bridge 里写的是真的 net.minecraft.client.Minecraft，
//    提前盖上去会被上面那步一起换掉，变成自我引用（找不到符号 —— 踩过）。
for (const ov of ['shim1122/java', 'forge1122/java']) {
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

// 5) 资源：共享的 lang 之类不要（1.12.2 的 png 在 textures/items），只用 forge1122/resources
cpSync(join(MODX, 'forge1122', 'resources'), join(W, 'classes'), { recursive: true, force: true });

// fabric.mod.json 之类不能进 Forge jar
for (const junk of ['fabric.mod.json', 'assets/mcver/items']) {
  const p = join(W, 'classes', junk);
  if (existsSync(p)) rmSync(p, { recursive: true, force: true });
}

const outDir = join(MODX, 'out', 'forge-12.2');
mkdirSync(outDir, { recursive: true });
const outJar = join(outDir, 'watercraftlauncher-forge-1.0.0-mc1.12.2.jar');
if (existsSync(outJar)) unlinkSync(outJar);
execFileSync(jarExe, ['cf', outJar, '-C', join(W, 'classes'), '.'], { stdio: 'inherit' });
console.log('OK ' + outJar);
