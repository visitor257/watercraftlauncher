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

  // 1.20.2 ~ 1.20.4（Forge 49/48）：运行时还是 SRG 名（m_/f_），所以走「先 mojmap 编、再 reobf 回 SRG」。
  // 编译用 modx/srg/<mc>/ 里由 scripts/mk-srg-mojmap.mjs 生成的两份 mojmap jar。
  // PoseStack 时代（1.19.4 及更早）：mojmap 里还没有 GuiGraphics，渲染要走 PoseStack。
  // 管线复用 SRG reobf，多出来的是 modx/forge19 里的 ui/Ctx + ChromeScreen 适配层。
  // MC 1.16.5 / Forge 36.2.42。三个特有的事：
  //   ① `RegistryObject` 在 `net.minecraftforge.fml`（1.17 才挪 fmllegacy、1.18.2 才回 registries）；
  //   ② 创造栏还是 `Item.Properties#tab(CreativeModeTab.TAB_TOOLS)`（`CreativeModeTabEvent` 1.19 才有）；
  //   ③ `--patched` 的 Forge client jar 只是**增量补丁包**（1275 个类），
  //      `mk-srg-mojmap.mjs` 现在会把「原版 remap」+「补丁 remap」合并成一份。
  // MC 1.14.4 / Forge 28.2.30 —— PoseStack 之前那一代：没有 PoseStack、
  // render(int,int,float)、renderBackground() 无参、Font 只收 String、Button 构造器也只收 String。
  // 详见 modx/forge14。
  'forge-14.4': {
    mc: '1.14.4',
    java: 8,
    instance: 'V1.14.4-FORGE',
    srg: true,
    loaderVer: '28.2.30',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[28,)',
    packFormat: 4,
    loaderDep: ['forge', '[28,)'],
    mcRange: '[1.14.4,1.15)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge14/java'],
    // 1.14.4 的零散改名：截字 substrByWidth（1.15 才叫 plainSubstrByWidth）、
    // EditBox 的占位符收 String（1.16 起收 Component）。只在生成出来的源码上改，
    // 不动共享源码，也不值得为两句开一份 overlay。
    srcReplace: [
      ['.plainSubstrByWidth(', '.substrByWidth('],
      ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
    ],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.14.4',
  },
  // MC 1.15.2 / Forge 31.2.62 —— 1.14.4 与 1.16.5 之间的过渡代。
  // 渲染 API 还是 1.14 那套（render(int,int,float)、无 PoseStack），但几个类改了名：
  // EditBox → TextFieldWidget、TextComponent → StringTextComponent、TextRenderer 的
  // getStringWidth → width(String)。适配都收在 modx/forge15。
  'forge-15.2': {
    mc: '1.15.2',
    java: 8,
    instance: '1.15.2-forge-31.2.62',
    srg: true,
    loaderVer: '31.2.62',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[31,)',
    packFormat: 5,
    loaderDep: ['forge', '[31,)'],
    mcRange: '[1.15.2,1.16)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge14/java', 'forge15/java'],
    srcReplace: [
      // 1.15.2 的 Font 还叫 substrByWidth（plainSubstrByWidth 是 1.16 改的名）
      ['.plainSubstrByWidth(', '.substrByWidth('],
      // 1.15 的 TextFieldWidget 占位符还是 String（1.16 起变 ITextComponent）
      ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
    ],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.15.2',
  },

  'forge-16.5': {
    mc: '1.16.5',
    java: 8,
    instance: 'V1.16.5-FORGE',
    srg: true,
    loaderVer: '36.2.42',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[36,)',
    packFormat: 6,
    loaderDep: ['forge', '[36,)'],
    mcRange: '[1.16.5,1.17)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge16/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.16.5',
  },
  'forge-17.1': {
    mc: '1.17.1',
    // MC 1.17 自带的是 Java 16（class 60）→ 编 17 会 UnsupportedClassVersionError
    java: 16,
    instance: 'V1.17.1-FORGE',
    srg: true,
    loaderVer: '37.1.1',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[37,)',
    packFormat: 7,
    loaderDep: ['forge', '[37,)'],
    mcRange: '[1.17.1,1.18)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge17/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.17.1',
  },
  'forge-18.2': {
    mc: '1.18.2',
    java: 17,
    instance: 'V1.18.2-FORGE',
    srg: true,
    loaderVer: '40.3.12',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[39,)',
    packFormat: 9,
    loaderDep: ['forge', '[40,)'],
    mcRange: '[1.18.2,1.19)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge18/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.18.2',
  },
  'forge-19.4': {
    mc: '1.19.4',
    java: 17,
    instance: 'V1.19.4-FORGE',
    srg: true,
    loaderVer: '45.4.5',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[45,)',
    packFormat: 13,
    loaderDep: ['forge', '[45,)'],
    mcRange: '[1.19.4,1.19.5)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge19/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.19.4',
  },
  'forge-20.4': {
    mc: '1.20.4',
    java: 17,
    instance: 'V1.20.4-FORGE',
    srg: true,
    loaderVer: '49.2.13',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[47,)',
    packFormat: 22,
    loaderDep: ['forge', '[49,)'],
    mcRange: '[1.20.4,1.20.5)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.20.4',
  },

  // ---- SRG 时代（1.20.1 ~ 1.20.4）：先 mojmap 编、再 reobf ----
  'forge-20.2': {
    mc: '1.20.2',
    java: 17,
    instance: 'V1.20.2-FORGE',
    srg: true,
    loaderVer: '48.1.0',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[47,)',
    packFormat: 18,
    loaderDep: ['forge', '[48,)'],
    mcRange: '[1.20.2,1.20.3)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.20.2',
  },

  'forge-20.1': {
    mc: '1.20.1',
    java: 17,
    instance: 'V1.20.1-FORGE',
    srg: true,
    loaderVer: '47.4.28',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[47,)',
    packFormat: 15,
    loaderDep: ['forge', '[47,)'],
    mcRange: '[1.20.1,1.20.2)',
    sources: ['mod/src/main/java'],
    overlay: ['shims201/java', 'forge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.20.1',
  },

  // NeoForge 20.2+ 的 client jar 已经是**官方名**（和 20.6/21.1 一样），不用 reobf。
  'neoforge-20.4': {
    mc: '1.20.4',
    java: 17,
    instance: 'V1.20.4-NEOFORGE',
    mandatory: true,
    extraCp: ['client-1.20.4-mojmap.jar'],
    clientJar: 'net/neoforged/neoforge/20.4.253/neoforge-20.4.253-client.jar',
    universalJar: 'net/neoforged/neoforge/20.4.253/neoforge-20.4.253-universal.jar',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[2,)',
    packFormat: 22,
    loaderDep: ['neoforge', '[20.4.0,)'],
    mcRange: '[1.20.4,1.20.5)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'neoforge20/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-neoforge-1.0.0-mc1.20.4',
  },

  'neoforge-20.2': {
    mc: '1.20.2',
    java: 17,
    instance: 'V1.20.2-NEOFORGE',
    mandatory: true,
    extraCp: ['client-1.20.2-mojmap.jar'],
    clientJar: 'net/neoforged/neoforge/20.2.93/neoforge-20.2.93-client.jar',
    universalJar: 'net/neoforged/neoforge/20.2.93/neoforge-20.2.93-universal.jar',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[1,)',
    packFormat: 18,
    loaderDep: ['neoforge', '[20.2.0,)'],
    mcRange: '[1.20.2,1.20.3)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'neoforge20/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-neoforge-1.0.0-mc1.20.2',
  },
  // TODO(NeoForge 20.1)：它其实就是 Forge 47 换皮——universal jar 里的包名还是 net.minecraftforge.*，
  // 而且 FML 是独立版本号（47.1.47 / 1.0.16）。要支持得另开一路 overlay，收益不大，暂缓。
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

  // TODO(MC 26.1 / Forge 62)：这一代三个新坑，都还没处理，先不做（探针已确认）：
  //   ① MC 的类是 class 69（Java 25）→ 必须用 Java 25 的 javac 编，且 --release 21 会直接报
  //      「类文件具有错误的版本 69.0，应为 65.0」（--release 会限制可读的类文件版本），
  //      得 --release 25 + jdk: 'testgame/runtime/java-runtime-epsilon/windows-x64'；
  //   ② ResourceLocation 改名成 net.minecraft.resources.Identifier（26.1 起）→ 转换表要加 era 分支；
  //   ③ loader 拆成一堆模块（fmlcore / eventbus / javafmllanguage / modlauncher ...），实例 json 里
  //      列不全 → 得把 srg 那套「按 <mc>-<loaderVer> 目录名补 classpath」的逻辑也用到这一代。
  'forge-21.4': {
    mc: '1.21.4',
    java: 21,
    instance: 'V1.21.4-FORGE',
    clientJar: 'net/minecraftforge/forge/1.21.4-54.1.22/forge-1.21.4-54.1.22-client.jar',
    universalJar: 'net/minecraftforge/forge/1.21.4-54.1.22/forge-1.21.4-54.1.22-universal.jar',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[25,)',
    packFormat: 46,
    loaderDep: ['forge', '[54,)'],
    mcRange: '[1.21.4,1.21.5)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge21/java', 'forge214/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.21.4',
  },

  'neoforge-21.4': {
    mc: '1.21.4',
    java: 21,
    instance: 'V1.21.4-NEOFORGE',
    clientJar: 'net/neoforged/neoforge/21.4.161/neoforge-21.4.161-client.jar',
    mcOfficialJar: 'net/minecraft/client/1.21.4/client-1.21.4-official.jar',
    universalJar: 'net/neoforged/neoforge/21.4.161/neoforge-21.4.161-universal.jar',
    modsToml: 'META-INF/neoforge.mods.toml',
    loaderRange: '[4,)',
    packFormat: 46,
    loaderDep: ['neoforge', '[21.4.0,)'],
    mcRange: '[1.21.4,1.21.5)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'neoforge21/java', 'neoforge214/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-neoforge-1.0.0-mc1.21.4',
  },
  'forge-20.6': {
    mc: '1.20.6',
    java: 21,
    instance: 'V1.20.6-FORGE',
    clientJar: 'net/minecraftforge/forge/1.20.6-50.2.14/forge-1.20.6-50.2.14-client.jar',
    universalJar: 'net/minecraftforge/forge/1.20.6-50.2.14/forge-1.20.6-50.2.14-universal.jar',
    modsToml: 'META-INF/mods.toml',
    loaderRange: '[24,)',
    packFormat: 32,
    loaderDep: ['forge', '[50,)'],
    mcRange: '[1.20.6,1.21)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'forge21/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-forge-1.0.0-mc1.20.6',
  },

  'neoforge-20.6': {
    mc: '1.20.6',
    java: 21,
    instance: 'neoforge-20.6.144',
    clientJar: 'net/neoforged/neoforge/20.6.144/neoforge-20.6.144-client.jar',
    mcOfficialJar: 'net/minecraft/client/1.20.6/client-1.20.6-official.jar',
    universalJar: 'net/neoforged/neoforge/20.6.144/neoforge-20.6.144-universal.jar',
    // NeoForge 20.5+ 把清单文件改名为 neoforge.mods.toml（20.2/20.4 还是 mods.toml）
    modsToml: 'META-INF/neoforge.mods.toml',
    // NeoForge 20.6 的 FML 是 3.x（21.x 才是 4.x，Forge 一直是 24+）
    loaderRange: '[3,)',
    packFormat: 32,
    loaderDep: ['neoforge', '[20.6.0,)'],
    mcRange: '[1.20.6,1.21)',
    sources: ['mod/src/main/java'],
    overlay: ['shims21/java', 'neoforge20/java'],
    resources: ['mod/src/main/resources'],
    jarName: 'watercraftlauncher-neoforge-1.0.0-mc1.20.6',
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

// 2.5) 逐 target 的源码级改写（`srcReplace`）。
// 只用于「同一块 mojmap 代码在不同世代名字不一样」但这种差异小到不值得开一份 overlay 的情形，
// 例如 1.14.4 的截字方法叫 Font#substrByWidth，1.15+ 才叫 plainSubstrByWidth。
if (T.srcReplace) {
  for (const [from, to] of T.srcReplace) {
    let n = 0;
    (function walk(dir) {
      for (const e of readdirSync(dir, { withFileTypes: true })) {
        const p = join(dir, e.name);
        if (e.isDirectory()) { walk(p); continue; }
        if (!p.endsWith('.java')) continue;
        const before = readFileSync(p, 'utf8');
        if (!before.includes(from)) continue;
        const after = before.split(from).join(to);
        writeFileSync(p, after, 'utf8');
        n += before.split(from).length - 1;
      }
    })(src);
    console.log(`  srcReplace ${from} -> ${to} (${n})`);
  }
}

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
  // 只跳过明确用不上的 natives / 非 Windows 平台包（它们里面没有 class，还会拖慢 javac）
  if (/(natives|linux|macos)/.test(lib.name)) continue;
  // SRG 模式下编译 classpath 只能用 mojmap 版，SRG 版的 MC/loader client 包必须踢掉
  // （否则同一个类在 SRG 名和官方名各出现一次，编译器会挑到 SRG 那份）
  if (T.srg && (lib.name.startsWith('net.minecraft:client:') || /:client$/.test(lib.name))) continue;
  const p = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path
    ? join(LIBS, lib.downloads.artifact.path)
    : mavenToPath(lib.name);
  if (existsSync(p)) { libJars.push(p); continue; }

  // 兜底：这个实例的 json 里没带全（例如 slf4j / lwjgl 只在别的版本段落里），
  // 就到 libraries 里按 group:artifact 找任意一个可用版本（编译只需要类型）。
  const [g, a] = lib.name.split(':');
  const base = join(LIBS, ...g.split('.'), a);
  let found = null;
  if (existsSync(base)) {
    for (const ver of readdirSync(base)) {
      const d = join(base, ver);
      if (!statSync(d).isDirectory()) continue;
      const jars = readdirSync(d).filter((n) => n.startsWith(a + '-') && n.endsWith('.jar') && !n.includes('-sources'));
      if (jars.length) { found = join(d, jars[0]); break; }
    }
  }
  if (found) libJars.push(found);
  else missing.push(lib.name);
}
// 补几个编译一定会用到、但某些实例 json 里没列（或只列了 natives classifier）的库。
// 判定「已存在」时必须排除 natives 包：json 里常见 org.lwjgl:lwjgl-glfw:3.3.3:natives-windows，
// 那是没有 class 的，光凭 group:artifact 出现就跳过补包 → 编译报「程序包 org.lwjgl.glfw 不存在」。
for (const name of [
  'org.slf4j:slf4j-api',
  'org.apache.logging.log4j:log4j-api',
  'org.lwjgl:lwjgl-glfw:3.3.3',
  'org.lwjgl:lwjgl:3.3.3',
  'com.google.code.gson:gson',
  // Forge 的 json 不含原版那一批 mojang 库（它们在父版本 json 里），而编译要用：
  'com.mojang:datafixerupper',
  'com.mojang:brigadier',
  'com.mojang:authlib',
  'com.mojang:logging',
  'com.mojang:blocklist',
  'com.mojang:patchy',
  'com.google.guava:guava',
  'it.unimi.dsi:fastutil',
  'org.joml:joml',
  'org.apache.commons:commons-lang3',
  'commons-io:commons-io',
]) {
  const [g, a, ver] = name.split(':');
  const already = libJars.some((p) => {
    const base = p.replace(/\\/g, '/').split('/').pop();
    return !base.includes('natives') && base.startsWith(a + '-') && base.endsWith('.jar');
  });
  if (already) continue;
  const base = join(LIBS, ...g.split('.'), a);
  if (!existsSync(base)) continue;
  const dirs = ver ? [join(base, ver)] : readdirSync(base).map((v) => join(base, v)).filter((d) => statSync(d).isDirectory()).sort().reverse();
  for (const dir of dirs) {
    if (!existsSync(dir)) continue;
    const jars = readdirSync(dir).filter((n) => n.startsWith(a + '-') && n.endsWith('.jar') && !n.includes('natives'));
    if (jars.length) { libJars.push(join(dir, jars[0])); break; }
  }
}

// SRG 时代的实例 json 常常漏掉 loader 自己的 jar（实测 Forge 1.20.1/1.20.2 的 json 里没有
// fmlcore，但 libraries 里躺着），@Mod / ModLoadingContext 就整包找不到。
// 按「目录名 == <mc>-<loaderVer> 或 <loaderVer>」精确补，不跨版本乱抓。
if (T.srg) {
  const stamps = new Set([`${T.mc}-${T.loaderVer}`, T.loaderVer]);
  for (const root of [join(LIBS, 'net', 'minecraftforge'), join(LIBS, 'net', 'neoforged')]) {
    if (!existsSync(root)) continue;
    for (const art of readdirSync(root)) {
      const artDir = join(root, art);
      if (!statSync(artDir).isDirectory()) continue;
      for (const ver of readdirSync(artDir)) {
        if (!stamps.has(ver)) continue;
        const vDir = join(artDir, ver);
        if (!statSync(vDir).isDirectory()) continue;
        for (const f of readdirSync(vDir)) {
          if (!f.endsWith('.jar') || /sources|javadoc|natives/.test(f)) continue;
          const full = join(vDir, f);
          if (!libJars.includes(full)) libJars.push(full);
        }
      }
    }
  }
}

if (missing.length) console.log('[warn] 缺库(不影响编译时就看有没有报错): ' + missing.slice(0, 6).join(', ') + (missing.length > 6 ? ' ...' : ''));
const srgDir = join(MODX, 'srg', T.mc);
const mojClient = join(srgDir, `client-${T.mc}-mojmap.jar`);
const mojUniversal = join(srgDir, `universal-${T.loaderVer}-mojmap.jar`);
const must = (T.srg
  ? [mojClient, mojUniversal]
  : [T.clientJar, T.mcOfficialJar, T.universalJar].filter(Boolean).map((p) => join(LIBS, p)));
// 有的 target 还需要「完整 mojmap 原版」（NeoForge 20.2/20.4 的 client jar 只含被改过的类）
for (const extra of T.extraCp || []) {
  const ejar = join(srgDir, extra);
  if (existsSync(ejar)) must.push(ejar);
  else console.log('[warn] extraCp 缺: ' + ejar);
}
// NeoForge 20.1 的 FML 是独立版本号（47.1.47 / 1.0.16），实例 json 里没有列全 → 显式补
if (T.srg && T.fmlVers) {
  for (const root of [join(LIBS, 'net', 'neoforged', 'fancymodloader'), join(LIBS, 'net', 'neoforged')]) {
    if (!existsSync(root)) continue;
    const arts = root.endsWith('fancymodloader') ? readdirSync(root).map((a) => join(root, a)) : [join(root, 'bus')];
    for (const art of arts) {
      if (!existsSync(art) || !statSync(art).isDirectory()) continue;
      for (const v of T.fmlVers) {
        const vDir = join(art, v);
        if (!existsSync(vDir)) continue;
        for (const f of readdirSync(vDir)) {
          if (!f.endsWith('.jar') || /sources|javadoc|natives/.test(f)) continue;
          const full = join(vDir, f);
          if (!libJars.includes(full)) libJars.push(full);
        }
      }
    }
  }
}
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
  '-proc:none',
  '-encoding', 'UTF-8',
  '--release', String(T.java),
  '-cp', `"${fw(cp)}"`,
  '-d', `"${fw(classes)}"`,
  ...sources.map((s) => `"${fw(s)}"`),
].join('\n'), 'utf8');

// 26.1 的 MC 类文件是版本 69（Java 25），JDK 21 的 javac 连读都读不了 → 允许按 target 换 JDK。
const JDK_HOME = T.jdk ? join(ROOT, T.jdk) : (process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7');
const javac = join(JDK_HOME, 'bin', 'javac.exe');
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
  // Forge 一律要；NeoForge 20.1~20.4（FML 1.x/2.x）也要，20.6+（FML 3/4）写了反而报未知字段
  ...(T.loaderDep[0] === 'forge' || T.mandatory ? ['mandatory=true'] : []),
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
const stagesJar = T.srg ? join(work, 'raw.jar') : jarPath;
rmSync(stagesJar, { force: true });
const jarExe = join(JDK_HOME, 'bin', 'jar.exe');
execFileSync(jarExe, ['--create', '--file', stagesJar, '-C', stages, '.'], { stdio: 'inherit' });

// SRG 时代：产出的字节码是官方名，运行时只认 SRG → reobf 一份
if (T.srg) {
  rmSync(jarPath, { force: true });
  const trCp = [
    ...['asm', 'asm-analysis', 'asm-commons', 'asm-tree', 'asm-util'].map((m) => moduleJar('org.ow2.asm/' + m, '9.9')),
    moduleJar('net.fabricmc/mapping-io', '0.8.0'),
    moduleJar('net.fabricmc/tiny-remapper', '0.12.0'),
  ].join(';');
  const javaExe = join(JDK_HOME, 'bin', 'java.exe');
  execFileSync(javaExe, [
    '-cp', trCp, 'net.fabricmc.tinyremapper.Main',
    stagesJar, jarPath,
    join(srgDir, 'mojmap2srg.tiny'), 'mojmap', 'srg',
    mojClient, mojUniversal, ...libJars,
  ], { stdio: 'inherit', maxBuffer: 1 << 28 });
}
console.log('OK ' + jarPath);

// Gradle 缓存里的模块 jar（tiny-remapper 的依赖）
function moduleJar(module, ver) {
  const base = join(process.env.USERPROFILE, '.gradle', 'caches', 'modules-2', 'files-2.1', ...module.split('/'), ver);
  for (const f of readdirSync(base)) {
    const sub = join(base, f);
    if (!statSync(sub).isDirectory()) continue;
    for (const g of readdirSync(sub)) if (g.endsWith('.jar') && !/sources|javadoc/.test(g)) return join(sub, g);
  }
  throw new Error('找不到 ' + module + '@' + ver);
}
