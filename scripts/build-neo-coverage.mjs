// 批量补 NeoForge 版本覆盖：安装 → （需要时）生成原版 mojmap jar → 写 target 配置 → 编译 → 收进 dist
//
//   node scripts/build-neo-coverage.mjs                 # 跑全部
//   node scripts/build-neo-coverage.mjs 1.20.3,1.21     # 只跑指定 MC
//   node scripts/build-neo-coverage.mjs --list
//
// 和 build-forge-coverage.mjs 的区别：NeoForge 20.2+ 的 `-client.jar` 已经是**官方名**，
// 编译产物不需要 reobf；但那份 client jar 只含 NeoForge 改过的类，所以还得配一份
// `modx/srg/<mc>/client-<mc>-mojmap.jar`（由 mk-srg-mojmap 生成）当 extraCp 补全。
//
// 每个版本单独记日志到 logs/neo-coverage/<mc>.log；失败不中断，最后打总表。
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, statSync, copyFileSync, writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { labelFor } from './mc-range-label.mjs';

const ROOT = resolve(import.meta.dirname, '..');
const JDK = process.env.MCVER_JDK21 || 'D:\\Program Files\\Java\\jdk-21';
const JAVA = join(JDK, 'bin', 'java.exe');
const TESTGAME = join(ROOT, 'testgame');
const LIBS = join(TESTGAME, 'libraries');
const DIST = join(ROOT, 'dist');
const PROBE_OUT = join(ROOT, 'tools', 'probe', 'out');
const LOGS = join(ROOT, 'logs', 'neo-coverage');
const EXTRA = join(ROOT, 'scripts', 'forge-extra-targets.json');
mkdirSync(LOGS, { recursive: true });

// 每个 MC 版本 → NeoForge 版本 + 世代参数。NeoForge 的版本号在 20.x 是「20.<mc 补丁>」，
// 21.x 起直接跟 MC 对齐（21.5.x == 1.21.5），已用 maven POM 逐个核对过。
// 标 beta 的是上游只有 beta（NeoForge 对这几个 MC 版本没发稳定版）。
const TARGETS = {
  // 1.20.1 的 NeoForge 走另一套坐标（net.neoforged:forge:1.20.1-47.1.106），
  // 包名还是 net.minecraftforge.*、**运行期还是 SRG**（ModLauncher 现场 remap），
  // 所以这里和 Forge 1.20.1 一样走「mojmap 编 + reobf 回 SRG」，overlay 也用 forge21。
  // 唯一的不同是 mods.toml 里依赖的 loader id 是 neoforge、且 FML 版本号是独立的一套。
  // **不单独出货**（2026-11-01 实测）：NeoForge 20.1 的 universal jar 里 mod id 仍然是 orge
  // （名字叫 NeoForge，loader 依赖 id 是 forge，FML 47.2.2），所以声明 depends.neoforge 会被判
  // Mod ID: 'neoforge' ... Actual version: '[MISSING]' 直接拒载。
  // 正确做法就是**直接用 Forge 1.20.1 那个 jar**——实测拿它装进 NeoForge 20.1 实例，像素验收通过
  // （center C6C6C6 / hits 15051 / children 17 / 870x519）。所以这里只留一条记录，不产出新 jar。
  // '1.20.1': { ... }  ← 有意注释掉，别再重新产出一个 depends.neoforge 的 1.20.1 jar
  // 1.20.1 的 NeoForge 走另一套坐标（net.neoforged:forge），包里还是 net.minecraftforge.*，
  // 单独处理（见 neo-1.20.1 分支），先留空。
  '1.20.3': { neo: '20.3.8-beta', java: 17, packFormat: 22, modsToml: 'META-INF/mods.toml', loaderRange: '[1,)', loaderDep: ['neoforge', '[20.3.0,)'], mandatory: true, overlay: ['shims21/java', 'neoforge20/java'] },
  '1.20.5': { neo: '20.5.21-beta', java: 21, packFormat: 32, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[3,)', loaderDep: ['neoforge', '[20.5.0,)'], overlay: ['shims21/java', 'neoforge21/java'] },
  '1.21': { neo: '21.0.167', java: 21, packFormat: 34, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.0.0,)'], overlay: ['shims21/java', 'neoforge21/java'] },
  '1.21.2': { neo: '21.2.1-beta', java: 21, packFormat: 42, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.2.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge214/java'] },
  '1.21.5': { neo: '21.5.101', java: 21, packFormat: 64, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.5.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java'] },
  '1.21.6': { neo: '21.6.20-beta', java: 21, packFormat: 64, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.6.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java'] },
  '1.21.7': { neo: '21.7.25-beta', java: 21, packFormat: 64, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.7.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java'] },
  '1.21.8': { neo: '21.8.57', java: 21, packFormat: 64, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.8.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java'] },
  '1.21.9': { neo: '21.9.16-beta', java: 21, packFormat: 69, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.9.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java', 'neoforge219/java'] },
  '1.21.10': { neo: '21.10.64', java: 21, packFormat: 69, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.10.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java', 'neoforge219/java'] },
  '1.21.11': { neo: '21.11.48', java: 21, packFormat: 75, modsToml: 'META-INF/neoforge.mods.toml', loaderRange: '[4,)', loaderDep: ['neoforge', '[21.11.0,)'], overlay: ['shims21/java', 'neoforge21/java', 'neoforge215/java', 'neoforge216/java', 'neoforge219/java'], srcReplace: [
    ['ResourceLocation', 'Identifier'], ['.location()', '.identifier()'], ['net.minecraft.Util;', 'net.minecraft.util.Util;'],
  ] },
};

const arg = process.argv[2];
if (arg === '--list' || !arg) {
  console.log('NeoForge 目标:');
  for (const [mc, t] of Object.entries(TARGETS)) console.log(`  ${mc.padEnd(8)} NeoForge ${t.neo}`);
  process.exit(0);
}
const mcs = arg.split(',').map((s) => s.trim()).filter(Boolean);

function log(name, text) { writeFileSync(join(LOGS, name + '.log'), text, 'utf8'); }
const upperPatch = (mc) => {
  const [a, b, c = 0] = mc.split('.').map(Number);
  return `${a}.${b}.${c + 1}`;
};

// 从 libraries 里找 NeoForge 的 client / universal jar（目录布局各代不同）
// 注意：**21.10 起不带 -client.jar 了**（实测 21.10.64 / 21.11.48 只发 universal），
// 所以 client 是可选的 —— 没有它时 vanilla 类全靠 mcOfficialJar 那份官方名全量 jar。
function findNeoJars(neo) {
  const cands = [
    join(LIBS, 'net', 'neoforged', 'neoforge', neo),
    join(LIBS, 'net', 'neoforged', 'forge', neo),
    join(LIBS, 'net', 'neoforged', 'forge', '1.20.1-' + neo),
  ];
  for (const dir of cands) {
    if (!existsSync(dir)) continue;
    const files = readdirSync(dir);
    const client = files.find((f) => f.endsWith('-client.jar'));
    const universal = files.find((f) => f.endsWith('-universal.jar'));
    if (universal) {
      const prefix = join('net', 'neoforged', dir.includes(join('neoforged', 'forge')) ? 'forge' : 'neoforge', dir.split(/[\\/]/).pop());
      return { clientJar: client ? join(prefix, client) : null, universalJar: join(prefix, universal), dir };
    }
  }
  return null;
}

const results = [];
for (const mc of mcs) {
  const t = TARGETS[mc];
  const key = 'neoforge-' + mc;
  const t0 = Date.now();
  let step = 'install';
  try {
    if (!t) throw new Error('不在目标表里');
    const neo = t.neo;
    // 20.1 的版本号自带 MC 前缀（1.20.1-47.1.106）；mk-srg 产出的 jar 名只带 47.1.106
    const neoShort = neo.startsWith(mc + '-') ? neo.slice(mc.length + 1) : neo;

    // 1) 安装（网络层失败会重试一次：maven.neoforged.net 实测会偶发 connect timeout）
    let instOut = '';
    let instName = null;
    for (let attempt = 1; attempt <= 2 && !instName; attempt++) {
      const inst = spawnSync(JAVA, ['-cp', PROBE_OUT, 'dev.mcver.probe.ProbeInstall',
        TESTGAME, mc, 'NEOFORGE', neo, 'NONE', '-', '-', '1'],
        { encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
      instOut += (inst.stdout || '') + (inst.stderr || '');
      // 注意 `resultVersionId=null` 也算失败：以前只判「有没有这个字段」，
      // 结果 null 被当成版本名，后面报成「libraries 里找不到 jar」，误导过一轮。
      const m = /resultVersionId=(\S+)/.exec(instOut);
      if (inst.status === 0 && m && m[1] !== 'null') instName = m[1];
      else if (attempt < 2) console.log(`     ${mc} 安装失败，重试一次`);
    }
    if (!instName) {
      log(mc, instOut);
      throw new Error('安装失败，详见 logs/neo-coverage/' + mc + '.log');
    }

    // 2) 原版类路径：新一代（1.21.5+、1.21、1.21.3）装机时直接给一份
    //    `client-<mc>-official.jar`（官方名、全量），直接拿来当 mcOfficialJar 就行，不用转映射。
    //    没有这份的（1.20.5 / 1.21.2）才需要 mk-srg-mojmap 生成 `client-<mc>-mojmap.jar`。
    step = 'srg';
    const officialRel = `net/minecraft/client/${mc}/client-${mc}-official.jar`;
    const hasOfficial = existsSync(join(LIBS, officialRel));
    const mojClient = join(ROOT, 'modx', 'srg', mc, `client-${mc}-mojmap.jar`);
    let srgLog = '';
    if (!hasOfficial && !existsSync(mojClient)) {
      // NeoForge 20.1 的版本号自带 MC 前缀（1.20.1-47.1.106），mk-srg 那边只认 `47.1.106`
      const r = spawnSync(process.execPath, [join(ROOT, 'scripts', 'mk-srg-mojmap.mjs'), mc, '--neo', neoShort],
        { encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
      srgLog = (r.stdout || '') + (r.stderr || '');
      if (r.status !== 0 || !existsSync(mojClient)) {
        log(mc, srgLog);
        throw new Error('mojmap 生成失败，详见 logs/neo-coverage/' + mc + '.log');
      }
    }

    // 3) 写 target 配置 + 编译
    step = 'build';
    const jars = findNeoJars(neo);
    if (!jars) throw new Error('libraries 里找不到 NeoForge client/universal jar: ' + neo);
    const extra = JSON.parse(existsSync(EXTRA) ? readFileSync(EXTRA, 'utf8') : '{}');
    extra[key] = {
      mc, java: t.java, instance: instName, loaderVer: t.srg ? neoShort : neo,
      // srg: true 的世代（NeoForge 20.1）和 Forge 一样：编译用 modx/srg/<mc>/ 里那份 mojmap jar，
      // 编完再 reobf 回 SRG；不走 clientJar/mcOfficialJar 那条「官方名直编」的路。
      ...(t.srg
        ? { srg: true }
        : {
            ...(jars.clientJar ? { clientJar: jars.clientJar } : {}), universalJar: jars.universalJar,
            // 有官方名全量 client jar 就直接用；没有才用 mk-srg 转出来的那份
            ...(hasOfficial ? { mcOfficialJar: officialRel } : { extraCp: [`client-${mc}-mojmap.jar`] }),
          }),
      modsToml: t.modsToml, loaderRange: t.loaderRange, packFormat: t.packFormat,
      loaderDep: t.loaderDep,
      // NeoForge 20.1~20.4（FML 1/2）的 mods.toml 要 mandatory 字段；20.6+（FML 3/4）写了反而报未知字段
      mandatory: t.mandatory === true,
      mcRange: `[${mc},${upperPatch(mc)})`,
      sources: ['mod/src/main/java'],
      overlay: t.overlay,
      resources: ['mod/src/main/resources'],
      srcReplace: t.srcReplace,
    };
    writeFileSync(EXTRA, JSON.stringify(extra, null, 2) + '\n', 'utf8');
    const b = spawnSync(process.execPath, [join(ROOT, 'scripts', 'build-neoforge.mjs'), key],
      { encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
    const bOut = (b.stdout || '') + (b.stderr || '');
    if (b.status !== 0) {
      log(mc, srgLog + bOut);
      throw new Error('编译失败，详见 logs/neo-coverage/' + mc + '.log');
    }

    // 4) 收进 dist
    const label = labelFor(extra[key].mcRange);
    const outJar = join(ROOT, 'modx', 'out', key, `watercraftlauncher-neoforge-1.0.0-mc${label}.jar`);
    if (!existsSync(outJar)) throw new Error('找不到产物: ' + outJar);
    copyFileSync(outJar, join(DIST, `watercraftlauncher-neoforge-1.0.0-mc${label}.jar`));
    results.push({ mc, ok: true, note: `NeoForge ${neo} -> mc${label}` });
    console.log(`OK   ${mc.padEnd(8)} NeoForge ${neo.padEnd(14)} -> mc${label}  (${Math.round((Date.now() - t0) / 1000)}s)`);
  } catch (e) {
    results.push({ mc, ok: false, note: `${step}: ${e.message}` });
    console.log(`FAIL ${mc.padEnd(8)} ${(TARGETS[mc] || {}).neo || '?'}  ${step}: ${e.message}`);
  }
}

console.log('\n=== 汇总 ===');
for (const r of results) console.log(`${r.ok ? '✔' : '✘'} ${r.mc.padEnd(8)} ${r.note}`);
const bad = results.filter((r) => !r.ok).length;
console.log(`成功 ${results.length - bad} / ${results.length}`);
process.exit(bad ? 1 : 0);
