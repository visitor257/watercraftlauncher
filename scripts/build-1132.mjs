// Legacy Fabric 通用构建（1.13 系列起）：javac 对 named jar → remap named→intermediary → 打 jar
//
//   node scripts/build-1132.mjs                     # 默认 1.13.2 / V1.13.2-FABRIC
//   node scripts/build-1132.mjs 1.13 --instance V1.13-FABRIC
//
// 前置：scripts/mk-legacy-named.mjs <mc> --instance <id>
//
// 和 build-1122.mjs 同一套管线（1.12 之后都没有 loom 可用）。差异：
//   - 1.13 的 Screen#init 已经是 protected，不用改成 public；
//   - 1.13 的贴图目录也是 textures/items（复数）；
//   - 资源清单沿用 1.14 的（没有 1.21.4 才有的 items/*.json item model definition）。
//   - compat 目录按「系列」取：有 compat/<mc> 就用它，没有就退到 compat/1.13.2
//     （1.13 和 1.13.1 与 1.13.2 的 API 是同一代，先复用，编译器报错再分叉）。
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';

const argv = process.argv.slice(2);
const MC = argv[0] && !argv[0].startsWith('--') ? argv[0] : '1.13.2';
const argOf = (k) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : null; };
const INSTANCE = argOf('--instance') || `V${MC}-FABRIC`;
// 一个 jar 盖一个系列时用（例：--mcrange ">=1.8 <1.9"）；默认只声明精确版本
const MC_RANGE = argOf('--mcrange') || MC;

const ROOT = resolve(import.meta.dirname, '..');
const MOD = join(ROOT, 'mod');
const LEG = join(ROOT, 'modx', 'legacy', MC);
const W = join(ROOT, 'modx', `build-${MC}`);
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';

const named = join(LEG, `client-${MC}-named.jar`);
const yarnTiny = join(LEG, 'work', 'yarn', 'mappings', 'mappings.tiny');
for (const p of [named, yarnTiny]) throwIfMissing(p);

function throwIfMissing(p) {
  if (!existsSync(p)) throw new Error('缺 ' + p + `（先跑 node scripts/mk-legacy-named.mjs ${MC} --instance ${INSTANCE}）`);
}

// ---- tiny-remapper 的 classpath（从 gradle 缓存里翻）----
const GC = join(process.env.USERPROFILE, '.gradle', 'caches', 'modules-2', 'files-2.1');
function firstJar(module, ver) {
  const p = join(GC, ...module.split('/'), ver);
  for (const f of readdirSync(p)) {
    const sub = join(p, f);
    if (!statSync(sub).isDirectory()) continue;
    for (const g of readdirSync(sub)) if (g.endsWith('.jar') && !/sources|javadoc/.test(g)) return join(sub, g);
  }
  throw new Error('找不到 ' + module + '@' + ver);
}
const TR_CP = [
  ...['asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util'].map((m) => firstJar('org.ow2.asm/' + m, '9.9')),
  firstJar('net.fabricmc/mapping-io', '0.8.0'),
  firstJar('net.fabricmc/tiny-remapper', '0.12.0'),
].join(';');

// ---- 找实例 json（安装器会把原版合并进实例目录，所以路径不一定是 versions/<mc>）----
function findInstanceJson() {
  for (const id of [INSTANCE, MC]) {
    const p = join(ROOT, 'testgame', 'versions', id, `${id}.json`);
    if (existsSync(p)) return p;
  }
  throw new Error(`找不到 ${INSTANCE} 或 ${MC} 的 version json`);
}

function compatDir() {
  const own = join(MOD, 'src', 'compat', MC, 'java');
  if (existsSync(own)) return { dir: own, note: `compat/${MC}` };
  // 按「API 代」复用：
  //   1.9~1.12 与 1.11.2 同一代（有 GuiScreen 血统、无 Element 体系）
  //   1.13.x   与 1.13.2 同一代（GUI 重构后、1.14 前）
  const FAMILIES = [[/^1\.8(\.|$)/, '1.8.9'], [/^1\.(9|10)(\.|$)/, '1.10.2'], [/^1\.(11|12)(\.|$)/, '1.11.2'], [/^1\.13(\.|$)/, '1.13.2'],
    // 1.7 家族分两种映射风格（都是 Legacy Fabric）：
    //   1.7.2~1.7.8 的 yarn 没映射 Screen/ButtonWidget 成员（method_219xx/field_225xx）→ compat/1.7.2
    //   1.7.10 映射是全的（render/tick/buttons）→ 它自己的目录
    // 实测 1.7.2/1.7.4 的中间名 id 完全一致，所以同一个 jar 能盖 1.7.2~1.7.8。
    [/^1\.7\.(2|3|4|5|6|7|8)(\.|$)/, '1.7.2'],
    [/^1\.7(\.|$)|^1\.7\.1(\.|$)/, '1.7.2']];
  const cands = [];
  for (const [re, dir] of FAMILIES) if (re.test(MC)) cands.push(dir);
  cands.push('1.14');
  for (const cand of cands) {
    const p = join(MOD, 'src', 'compat', cand, 'java');
    if (existsSync(p)) return { dir: p, note: `compat/${cand}（借用）` };
  }
  throw new Error(`没有可用的 compat 目录（${MC}）`);
}
const COMPAT_INFO = compatDir();
const COMPAT = COMPAT_INFO.dir;
const GEN = join(W, 'gen');
rmSync(GEN, { recursive: true, force: true });
mkdirSync(GEN, { recursive: true });

// 把 1.14+ 的写法改写成本 target 能编的东西。1.13 缺的：
//   X.execute(...)                    -> dev.mcver.core.Ui.exec(X, ...)      （只有 Callable 版）
//   keyboard.setClipboard(x)          -> dev.mcver.core.Ui.clipboard(x)     （keyboard 字段没命名）
//   f.setChangedListener(...)         -> dev.mcver.ui.Field.changed(f, ...) （1.13 是 BiConsumer<Integer,String>）
//   Util.getOperatingSystem().open(u) -> dev.mcver.core.Open.uri(u)
function rewrite(s) {
  s = s.replace(/MinecraftClient\.getInstance\(\)\.keyboard\.setClipboard\(/g, 'dev.mcver.core.Ui.clipboard(');
  s = s.replace(/Util\.getOperatingSystem\(\)\.open\(URI\.create\(([^()]*)\)\)/g, 'dev.mcver.core.Open.uri($1)');
  s = s.replace(/Util\.getOperatingSystem\(\)\.open\(java\.net\.URI\.create\(([^()]*)\)\)/g, 'dev.mcver.core.Open.uri($1)');
  s = s.replace(/([A-Za-z_][\w]*)\.setChangedListener\(/g, 'dev.mcver.ui.Field.changed($1, ');
  // 有的 target 的 Screen#init() 是 public（1.11.2 / 1.12.2），子类不能用 protected 降权；
  // 1.13+ 是 protected，改成 public 也无害（只能放宽）。
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
// compat 是手写的，不能再 rewrite（否则 Ui.exec 会套两层）
scan(COMPAT, COMPAT, join(GEN, 'compat'), false);
console.log(`源码 ${byRel.size} 个（main ${mainCount} + compat 新增 ${byRel.size - mainCount}，来源 ${COMPAT_INFO.note}）`);

const srcList = join(W, 'sources.txt');
mkdirSync(W, { recursive: true });
writeFileSync(srcList, [...byRel.values()].join('\n'), 'utf8');

// ---- javac 的 classpath --------------------------------------------------------
// inherit（1.7 这种没被 flatten 掉的版本）要看继承链：加载器的 json 里只有它自己的 11 个库，
// 原版那 25 个（log4j / guava / netty…）在父版本的 json 里 —— 不跟链走就会「找不到 log4j」。
function chainLibs(id, seenIds) {
  if (seenIds.has(id)) return [];
  seenIds.add(id);
  const p = join(ROOT, 'testgame', 'versions', id, `${id}.json`);
  if (!existsSync(p)) return [];
  const json = JSON.parse(readFileSync(p, 'utf8'));
  const parent = json.inheritsFrom ? chainLibs(json.inheritsFrom, seenIds) : [];
  return [...(json.libraries || []), ...parent];
}
function libCp() {
  const jsonPath = findInstanceJson();
  const json = JSON.parse(readFileSync(jsonPath, 'utf8'));
  const out = [named];
  const seen = new Set();
  const libs = [
    ...(json.libraries || []),
    ...(json.inheritsFrom ? chainLibs(json.inheritsFrom, new Set([json.id])) : []),
  ];
  for (const lib of libs) {
    const parts = lib.name.split(':');
    const [group, artifact, version, classifier] = parts;
    if (classifier && /native/i.test(classifier)) continue;
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.jar';
    if (/-natives/.test(rel)) continue;
    const p = join(ROOT, 'testgame', 'libraries', rel);
    if (!existsSync(p) || seen.has(p)) continue;
    seen.add(p);
    out.push(p);
  }
  // 1.7 的 log4j 只有父版本声明，且下载器只抓了 2.0-beta9 之外的东西 —— 补上。
  for (const rel of [
    'org/apache/logging/log4j/log4j-api/2.0-beta9/log4j-api-2.0-beta9.jar',
    'org/apache/logging/log4j/log4j-core/2.0-beta9/log4j-core-2.0-beta9.jar',
  ]) {
    const p = join(ROOT, 'testgame', 'libraries', rel);
    if (existsSync(p) && !seen.has(p)) out.push(p);
  }
  // fabric-loader 本身（入口接口 ModInitializer / ClientModInitializer）
  for (const rel of ['net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.jar']) {
    const p = join(ROOT, 'testgame', 'libraries', rel);
    if (existsSync(p) && !seen.has(p)) out.push(p);
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

cpSync(join(MOD, 'src', 'main', 'resources', 'assets'), join(res, 'assets'), { recursive: true });
// 1.21.4+ 才有的 item model definition，1.13 不认
rmSync(join(res, 'assets', 'mcver', 'items'), { recursive: true, force: true });
// 1.13 的贴图目录是 textures/items（复数）
mkdirSync(join(res, 'assets', 'mcver', 'textures', 'items'), { recursive: true });
cpSync(join(res, 'assets', 'mcver', 'textures', 'item', 'download_tool.png'),
  join(res, 'assets', 'mcver', 'textures', 'items', 'download_tool.png'));
rmSync(join(res, 'assets', 'mcver', 'textures', 'item'), { recursive: true, force: true });
writeFileSync(join(res, 'assets', 'mcver', 'models', 'item', 'download_tool.json'), JSON.stringify({
  parent: 'item/generated',
  textures: { layer0: 'mcver:items/download_tool' },
}, null, 2));
// 1.6~1.12 的资源包需要 pack.mcmeta：Fabric loader 把 mod jar 当一个资源包挂上，
// 老版本的 ResourcePack 读 meta 时会先找它；没有就整个包不生效 ——
// 表现是物品贴图 FileNotFoundException（"unable to load mcver:textures/items/download_tool.png"）。
writeFileSync(join(res, 'pack.mcmeta'), JSON.stringify({
  pack: { pack_format: 1, description: 'Water Craft Launcher' },
}, null, 2));
// 配方：1.13 起挪到 data/<modid>/recipes/
const compatRes = join(MOD, 'src', 'compat', '1.14', 'resources');
if (existsSync(compatRes)) cpSync(compatRes, res, { recursive: true });

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
    minecraft: MC_RANGE,
  },
}, null, 2));

// ---- 打 jar → remap ------------------------------------------------------------
const preJar = join(W, 'named.jar');
rmSync(preJar, { force: true });
execFileSync(join(JDK, 'jar.exe'), ['cf', preJar, '-C', classes, '.', '-C', res, '.'], { stdio: 'inherit' });

const dist = join(ROOT, 'dist');
mkdirSync(dist, { recursive: true });
const outJar = join(dist, `watercraftlauncher-fabric-1.0.0-mc${MC}.jar`);
rmSync(outJar, { force: true });
console.log('remap named -> intermediary');
execFileSync(join(JDK, 'java.exe'), [
  '-cp', TR_CP, 'net.fabricmc.tinyremapper.Main',
  preJar, outJar, yarnTiny, 'named', 'intermediary', named,
], { stdio: 'inherit' });

console.log('\n产出 ' + outJar + '  (' + Math.round(statSync(outJar).size / 1024) + 'KB)');
