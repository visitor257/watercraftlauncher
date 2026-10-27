// NeoForge / Forge 的 javac 构建器（不用 Gradle、不用 NeoGradle）。
//
// 为什么能这么干：NeoForge 20.2+ 起，游戏内跑的 MC 类就是**官方名（mojmap）**，
// 安装到 .minecraft/libraries 里的 net.neoforged:neoforge:<v>:client 就是那份打好补丁的
// 客户端类；universal 里是 NeoForge 自己的 API。两者拼起来就是一个完整的编译期 classpath，
// 编出来的字节码运行时名字一模一样 —— 不需要 reobf。
// （Forge 1.16~1.20.1 还得 reobf，见 build-forge。这里先只做 NeoForge。）
//
// 用法：
//   node scripts/build-neoforge.mjs <target>
// target 见 TARGETS；产物落 modx/out/<target>/<jarName>.jar

import { execFileSync } from 'node:child_process';
import {
  cpSync, existsSync, mkdirSync, readdirSync, rmSync, statSync, writeFileSync, readFileSync,
} from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const LIBS = join(ROOT, 'testgame', 'libraries');
const MODX = join(ROOT, 'modx');

// 每个 target：MC 版本、NeoForge 版本、client/universal jar 在 libraries 里的路径、Java 语言级
const TARGETS = {
  'neoforge-21.1': {
    mc: '1.21.1',
    neo: '21.1.265',
    java: 21,
    instance: 'neoforge-21.1.265',
    // 编译期 classpath 三段：
    //   ① NeoForge 打过补丁的类（只有它改过的那些，必须排在前面遮住原版）
    //   ② 完整 MC 客户端、官方名（安装 NeoForge 时下载的 net.minecraft:client:<mc>:official）
    //   ③ NeoForge API（universal）
    clientJar: 'net/neoforged/neoforge/21.1.265/neoforge-21.1.265-client.jar',
    mcOfficialJar: 'net/minecraft/client/1.21.1/client-1.21.1-official.jar',
    universalJar: 'net/neoforged/neoforge/21.1.265/neoforge-21.1.265-universal.jar',
    modsToml: 'META-INF/neoforge.mods.toml',
    loaderRange: '[4,)',
    packFormat: 34,
    // 依赖声明：[modId, versionRange]
    loaderDep: ['neoforge', '[21.1.0,)'],
    mcRange: '[1.21.1,1.22)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'neoforge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-neoforge-1.0.0-mc1.21.1',
  },

  'forge-21.1': {
    mc: '1.21.1',
    java: 21,
    instance: 'V1.21.1-FORGE',
    // Forge 1.20.2+ 与 NeoForge 一样在运行时用官方名：
    // forge-<ver>-client.jar 就是「完整 MC + Forge 补丁」的官方名版本，不需要 reobf。
    clientJar: 'net/minecraftforge/forge/1.21.1-52.1.20/forge-1.21.1-52.1.20-client.jar',
    universalJar: 'net/minecraftforge/forge/1.21.1-52.1.20/forge-1.21.1-52.1.20-universal.jar',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[24,)',
    packFormat: 34,
    loaderDep: ['forge', '[52,)'],
    mcRange: '[1.21.1,1.22)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.21.1',
  },
};

const targetName = process.argv[2];
const T = TARGETS[targetName];
if (!T) {
  console.error('未知 target: ' + targetName + '\n可选: ' + Object.keys(TARGETS).join(', '));
  process.exit(2);
}

const work = join(MODX, 'build', targetName);
const src = join(work, 'src');
const classes = join(work, 'classes');
const stages = join(work, 'stages');
const outDir = join(MODX, 'out', targetName);

for (const d of [work]) rmSync(d, { recursive: true, force: true });
mkdirSync(src, { recursive: true });

// 1) yarn → mojmap 机械转换
const roots = T.sources.map((s) => join(ROOT, s)).join(';');
execFileSync(process.execPath, [join(ROOT, 'scripts', 'mk-mojmap-src.mjs'), src, roots], { stdio: 'inherit' });

// 2) 手写适配层 / loader 入口覆盖上去（同路径同名 = 手写赢）
for (const ov of T.overlay) cpSync(join(MODX, ov), src, { recursive: true, force: true });

// 3) 编译
//
// classpath 必须**精确**取自该实例的 version json，而不是「libraries 下所有 jar」：
// libraries 里同时躺着 1.16.5 ~ 26.1 十几个版本的 MC/loader 类（还有 fancymodloader 11.x），
// 谁排在前面谁赢，结果就是拿着 26.1 的 CreativeModeTabs 去编 1.21.1（踩过）。
function mavenToPath(name) {
  const [g, a, v, cls] = name.split(':');
  const base = join(LIBS, ...g.split('.'), a, v, `${a}-${v}${cls ? '-' + cls : ''}.jar`);
  return base;
}

const instJson = JSON.parse(readFileSync(join(ROOT, 'testgame', 'versions', T.instance, T.instance + '.json'), 'utf8'));
const libJars = [];
const missing = [];
for (const lib of instJson.libraries || []) {
  const p = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path
    ? join(LIBS, lib.downloads.artifact.path)
    : mavenToPath(lib.name);
  if (existsSync(p)) libJars.push(p);
  else missing.push(lib.name);
}
if (missing.length) console.log('[warn] 缺库(不影响编译时就看有没有报错): ' + missing.slice(0, 6).join(', ') + (missing.length > 6 ? ' ...' : ''));
const must = [T.clientJar, T.mcOfficialJar, T.universalJar].filter(Boolean).map((p) => join(LIBS, p));
for (const m of must) if (!existsSync(m)) { console.error('缺 jar: ' + m); process.exit(1); }

const cp = must.concat(libJars).join(';');
const sources = [];
(function walk(dir) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p);
    else if (name.endsWith('.java')) sources.push(p);
  }
})(src);

mkdirSync(classes, { recursive: true });
// javac 的 @argfile 里反斜杠是转义符，Windows 路径直接写会被吃掉。统一换成正斜杠。
const fw = (p) => p.replace(/\\/g, '/');
const argFile = join(work, 'javac.args');
writeFileSync(argFile, [
  '-nowarn',
  '-encoding', 'UTF-8',
  '--release', String(T.java),
  '-cp', `"${fw(cp)}"`,
  '-d', `"${fw(classes)}"`,
  ...sources.map((s) => `"${fw(s)}"`),
].join('\n'), 'utf8');

const javac = join(process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7', 'bin', 'javac.exe');
try {
  execFileSync(javac, ['@' + argFile], { stdio: 'inherit' });
} catch {
  console.error('编译失败');
  process.exit(1);
}

// 4) 打包：classes + resources + mods.toml
rmSync(stages, { recursive: true, force: true });
mkdirSync(stages, { recursive: true });
cpSync(classes, stages, { recursive: true });
for (const r of T.resources) cpSync(join(ROOT, r), stages, { recursive: true });

const tomlPath = join(stages, T.modsToml);
mkdirSync(join(stages, 'META-INF'), { recursive: true });

// Forge/NeoForge 把 mod jar 当资源包加载，**必须有根目录的 pack.mcmeta**，否则
// 模型/贴图一条都读不到（实测 Forge 上就是 FileNotFoundException: mcver:models/item/...
// + Missing metadata in pack mod:mcver）。Fabric 不需要，所以这个文件只写进这套产物。
writeFileSync(join(stages, 'pack.mcmeta'), JSON.stringify({
  pack: { description: 'Water Craft Launcher resources', pack_format: T.packFormat },
}, null, 2), 'utf8');
const depBlock = (modId, range) => [
  '[[dependencies.mcver]]',
  `modId="${modId}"`,
  // Forge 的 mods.toml 要求 mandatory 字段（NeoForge 去掉了它）；两边都写会被 NeoForge 报未知字段，
  // 所以按 loader 分流（实测：缺 mandatory 会让 Forge 直接拒绝加载这个 jar）。
  ...(T.loaderDep[0] === 'forge' ? ['mandatory=true'] : []),
  'type="required"',
  `versionRange="${range}"`,
  'ordering="NONE"',
  'side="CLIENT"',
  '',
].join('\n');

writeFileSync(tomlPath, `modLoader="javafml"
loaderVersion="${T.loaderRange}"
license="MIT"
issueTrackerURL="https://example.invalid"

[[mods]]
modId="mcver"
version="1.0.0"
displayName="Water Craft Launcher"
description='''在游戏内下载、安装并启动其他 Minecraft 版本（Fabric / Quilt / Forge / NeoForge / Iris / OptiFine）。'''

${depBlock(T.loaderDep[0], T.loaderDep[1])}${depBlock('minecraft', T.mcRange)}`, 'utf8');

mkdirSync(outDir, { recursive: true });
const jarPath = join(outDir, T.jarName + '.jar');
rmSync(jarPath, { force: true });
const jarExe = join(process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7', 'bin', 'jar.exe');
execFileSync(jarExe, ['--create', '--file', jarPath, '-C', stages, '.'], { stdio: 'inherit' });
console.log('OK ' + jarPath);
