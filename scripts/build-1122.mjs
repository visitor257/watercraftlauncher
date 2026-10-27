// 1.12.2（Legacy Fabric）构建：javac 对 named jar → remap named→intermediary → 打 jar
//
// 跟 1.16+ 那套（Gradle/loom）的区别：1.12 没有 loom 可用（ornithe-loom 要 Gradle 9 + 另一套插件），
// 所以直接 javac + tiny-remapper。源目录与其它 target 一致：
//   mod/src/main/java  +  mod/src/compat/1.12.2/java（同路径覆盖）
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const MOD = join(ROOT, 'mod');
const LEG = join(ROOT, 'modx', 'legacy');
const W = join(ROOT, 'modx', 'build-1.12.2');
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';

const named = join(LEG, 'client-1.12.2-named.jar');
const yarnTiny = join(LEG, 'work', 'yarn', 'mappings', 'mappings.tiny');
for (const p of [named, yarnTiny]) if (!existsSync(p)) throw new Error('缺 ' + p + '（先跑 scripts/mk-1122-named.mjs）');

const GC = join(process.env.USERPROFILE, '.gradle', 'caches', 'modules-2', 'files-2.1');
function firstJar(module, ver) {
  const p = join(GC, ...module.split('/'), ver);
  for (const f of readdirSync(p)) {
    const sub = join(p, f);
    for (const g of readdirSync(sub)) if (g.endsWith('.jar') && !/sources|javadoc/.test(g)) return join(sub, g);
  }
  throw new Error('找不到 ' + module + '@' + ver);
}
const TR_CP = [
  ...['asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util'].map((m) => firstJar('org.ow2.asm/' + m, '9.9')),
  firstJar('net.fabricmc/mapping-io', '0.8.0'),
  firstJar('net.fabricmc/tiny-remapper', '0.12.0'),
].join(';');

// ---- 收集源码：compat 覆盖 main ------------------------------------------------
const COMPAT = join(MOD, 'src', 'compat', '1.12.2', 'java');
const GEN = join(W, 'gen');
rmSync(GEN, { recursive: true, force: true });
mkdirSync(GEN, { recursive: true });

// 把一个 java 文件里的 1.14+ 写法改写掉（1.12 没有这些方溢）：
//   X.execute(...)                    -> dev.mcver.core.Ui.exec(X, ...)
//   MinecraftClient.getInstance().keyboard.setClipboard(x) -> dev.mcver.core.Ui.clipboard(x)
function rewrite(s) {
  s = s.replace(/MinecraftClient\.getInstance\(\)\.keyboard\.setClipboard\(/g, 'dev.mcver.core.Ui.clipboard(');
  s = s.replace(/Util\.getOperatingSystem\(\)\.open\(URI\.create\(([^()]*)\)\)/g, 'dev.mcver.core.Open.uri($1)');
  s = s.replace(/Util\.getOperatingSystem\(\)\.open\(java\.net\.URI\.create\(([^()]*)\)\)/g, 'dev.mcver.core.Open.uri($1)');
  // 1.12 的 TextFieldWidget 没有 setChangedListener，走轮询 shim
  s = s.replace(/([A-Za-z_][\w]*)\.setChangedListener\(/g, 'dev.mcver.ui.Field.changed($1, ');
  // 1.12 的 Screen#init() 是 public，子类不能用 protected 降权
  s = s.replace(/protected void init\(\)/g, 'public void init()');
  s = s.replace(/(MinecraftClient\.getInstance\(\)|\b[A-Za-z_][\w]*\(\)|\b[A-Za-z_][\w]*)\.execute\(/g,
    (m, recv) => (recv === 'Ui' ? m : 'dev.mcver.core.Ui.exec(' + recv + ', '));
  return s;
}

const byRel = new Map();
function scan(dir, base, genRoot, doRewrite) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) { scan(p, base, genRoot, doRewrite); continue; }
    if (!f.endsWith('.java')) continue;
    const rel = relative(base, p);
    const dest = join(genRoot, rel);
    mkdirSync(join(dest, '..'), { recursive: true });
    const src = readFileSync(p, 'utf8');
    writeFileSync(dest, doRewrite ? rewrite(src) : src, 'utf8');
    byRel.set(rel, dest);
  }
}
scan(join(MOD, 'src', 'main', 'java'), join(MOD, 'src', 'main', 'java'), join(GEN, 'main'), true);
const mainCount = byRel.size;
// compat 是手写的 1.12 版本，不能再被 rewrite（否则 Ui.exec 会被套两层）
scan(COMPAT, COMPAT, join(GEN, 'compat'), false); // 同相对路径直接盖掉
console.log(`源码 ${byRel.size} 个（main ${mainCount} + compat 覆盖 ${byRel.size - mainCount}）`);

const srcList = join(W, 'sources.txt');
mkdirSync(W, { recursive: true });
writeFileSync(srcList, [...byRel.values()].join('\n'), 'utf8');

// javac 的 classpath：MC named jar + 1.12.2 的 libraries（log4j2 / lwjgl2 / guava…）+ fabric-loader
function libCp() {
  const json = JSON.parse(readFileSync(join(ROOT, 'testgame', 'versions', '1.12.2', '1.12.2.json'), 'utf8'));
  const out = [named];
  for (const lib of json.libraries) {
    const [group, artifact, version] = lib.name.split(':');
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.jar';
    if (/-natives/.test(rel)) continue;
    const p = join(ROOT, 'testgame', 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  for (const rel of [
    'net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar',
    'org/lwjgl/lwjgl/lwjgl/2.9.4+legacyfabric.17/lwjgl-2.9.4+legacyfabric.17.jar',
    'org/lwjgl/lwjgl/lwjgl_util/2.9.4+legacyfabric.17/lwjgl_util-2.9.4+legacyfabric.17.jar',
    'net/legacyfabric/intermediary/1.12.2/intermediary-1.12.2.jar',
  ]) {
    const p = join(ROOT, 'testgame', 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out.join(';');
}
const CP = libCp();

// ---- javac -------------------------------------------------------------------
const classes = join(W, 'classes');
rmSync(classes, { recursive: true, force: true });
mkdirSync(classes, { recursive: true });
console.log('javac（--release 8，对 named jar）');
execFileSync(join(JDK, 'javac.exe'), [
  '-proc:none', '-nowarn', '-encoding', 'UTF-8', '--release', '8',
  '-cp', CP, '-d', classes, '@' + srcList,
], { stdio: 'inherit' });

// ---- 资源 --------------------------------------------------------------------
const res = join(W, 'res');
rmSync(res, { recursive: true, force: true });
mkdirSync(res, { recursive: true });

// 1) 主资源：models/item、lang、textures（1.12 的贴图目录是 textures/items，复数）
cpSync(join(MOD, 'src', 'main', 'resources', 'assets'), join(res, 'assets'), { recursive: true });
mkdirSync(join(res, 'assets', 'mcver', 'textures', 'items'), { recursive: true });
cpSync(join(res, 'assets', 'mcver', 'textures', 'item', 'download_tool.png'),
  join(res, 'assets', 'mcver', 'textures', 'items', 'download_tool.png'));
rmSync(join(res, 'assets', 'mcver', 'textures', 'item'), { recursive: true, force: true });
rmSync(join(res, 'assets', 'mcver', 'items'), { recursive: true, force: true }); // 1.21.4+ 才有的 item model definition
// 1.12 的贴图目录是 textures/items（复数），模型里的 layer0 也要跟着改，否则图标渲染成白块
writeFileSync(join(res, 'assets', 'mcver', 'models', 'item', 'download_tool.json'), JSON.stringify({
  parent: 'item/generated',
  textures: { layer0: 'mcver:items/download_tool' },
}, null, 2));
// 1.12 的配方在 assets/<modid>/recipes/，且是 1.12 的 crafting_shaped 格式
const compatRes = join(MOD, 'src', 'compat', '1.12.2', 'resources');
if (existsSync(compatRes)) cpSync(compatRes, res, { recursive: true });

// 2) fabric.mod.json（1.12.2 专用）
writeFileSync(join(res, 'fabric.mod.json'), JSON.stringify({
  schemaVersion: 1,
  id: 'mcver',
  version: '1.0.0',
  name: 'Water Craft Launcher',
  description: '在游戏内下载/安装 Minecraft 版本、管账号、启动别的版本。',
  authors: ['WaterCraft'],
  license: 'MIT',
  environment: 'client',
  entrypoints: {
    main: ['dev.mcver.McVerMod'],
    client: ['dev.mcver.McVerClient'],
  },
  depends: {
    fabricloader: '>=0.14.0',
    minecraft: '1.12.2',
    java: '>=1.8',
  },
}, null, 2));

// ---- 打 jar（named）→ remap 成 intermediary ------------------------------------
const preJar = join(W, 'named.jar');
rmSync(preJar, { force: true });
execFileSync(join(JDK, 'jar.exe'), ['cf', preJar, '-C', classes, '.', '-C', res, '.'], { stdio: 'inherit' });

const dist = join(ROOT, 'dist');
mkdirSync(dist, { recursive: true });
const outJar = join(dist, 'watercraftlauncher-fabric-1.0.0-mc1.12.2.jar');
rmSync(outJar, { force: true });
console.log('remap named -> intermediary');
execFileSync(join(JDK, 'java.exe'), [
  '-cp', TR_CP, 'net.fabricmc.tinyremapper.Main',
  preJar, outJar, yarnTiny, 'named', 'intermediary', named,
], { stdio: 'inherit' });

console.log('\n产出 ' + outJar + '  (' + Math.round(statSync(outJar).size / 1024) + 'KB)');
