// 批量补齐 Forge 版本覆盖：安装 → （需要时）生成映射 → 编译 → 收进 dist
//
//   node scripts/build-forge-coverage.mjs modern     # 1.20.3 / 1.21 / 1.21.3 / 1.21.5~1.21.11（mojmap 直编）
//   node scripts/build-forge-coverage.mjs srg        # 1.14.2~1.20（要 mk-srg-mojmap 先把 SRG 转 mojmap）
//   node scripts/build-forge-coverage.mjs --list
//
// 每步单独记日志到 logs/forge-coverage/<mc>.log；失败不中断，最后打总表。
// 前置：tools/probe/out（scripts/build-probe.ps1 产出）、网络可达 Forge maven。
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, statSync, copyFileSync, writeFileSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { labelFor } from './mc-range-label.mjs';

const ROOT = resolve(import.meta.dirname, '..');
const JDK = process.env.MCVER_JDK21 || 'D:\\Program Files\\Java\\jdk-21';
const JAVA = join(JDK, 'bin', 'java.exe');
const TESTGAME = join(ROOT, 'testgame');
const DIST = join(ROOT, 'dist');
const PROBE_OUT = join(ROOT, 'tools', 'probe', 'out');
const LOGS = join(ROOT, 'logs', 'forge-coverage');
const EXTRA = join(ROOT, 'scripts', 'forge-extra-targets.json');
mkdirSync(LOGS, { recursive: true });

const builds = JSON.parse(readFileSync(join(ROOT, '.tools', 'forge-builds.json'), 'utf8'));

// 显式世代表：13 个「只差一个补丁号」的版本，参数一律抄同代最后一个补丁的 target
// （见 build-neoforge.mjs 的 TARGETS：1.14.4 / 1.15.2 / 1.16.5 / 1.17.1 / 1.18.2 / 1.19.4 / 1.20.1）。
// java / packFormat / overlay 是**世代**属性，不会因为补丁号变；靠下面的 if 链猜必错：
// 原来 1.15/1.16 落到 `b >= 15` 那条，编出来是 class 61（Java 17）—— 而 1.15/1.16 跑在 Java 8 上，
// 一装就 UnsupportedClassVersionError；1.16 还会错挂 forge15 overlay 而不是 forge16。
const ERA = {
  '1.15': { java: 8, packFormat: 5, srg: true, overlay: ['shims21/java', 'forge14/java', 'forge15/java'], srcReplace: [
    // 1.15 的 Font 还叫 substrByWidth（plainSubstrByWidth 是 1.16 改的名）
    ['.plainSubstrByWidth(', '.substrByWidth('],
    // 1.15 的 TextFieldWidget 占位符还是 String（1.16 起变 ITextComponent）
    ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
  ] },
  '1.15.1': { java: 8, packFormat: 5, srg: true, overlay: ['shims21/java', 'forge14/java', 'forge15/java'], srcReplace: [
    ['.plainSubstrByWidth(', '.substrByWidth('],
    ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
  ] },
  // 1.14.2 / 1.14.3：Mojang 那两代**没发官方映射**（1.14.4 才开始发）。
  // 走「借映射」：srg 名跨补丁版本稳定，1.14.x 的 srg jar 类名又是真实包名，
  // 所以直接拿 1.14.4 的 srg2mojmap.tiny / mojmap2srg.tiny 用（见 mk-srg-mojmap 的 --tinys-from）。
  // 其余参数（java 8 / pack_format 4 / shims21+forge14）与同代 1.14.4 完全一致。
  '1.14.2': { java: 8, packFormat: 4, srg: true, patched: true, tinysFrom: '1.14.4', overlay: ['shims21/java', 'forge14/java', 'forge142/java'], srcReplace: [
    ['.plainSubstrByWidth(', '.substrByWidth('],
    ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
  ] },
  '1.14.3': { java: 8, packFormat: 4, srg: true, patched: true, tinysFrom: '1.14.4', overlay: ['shims21/java', 'forge14/java', 'forge142/java'], srcReplace: [
    ['.plainSubstrByWidth(', '.substrByWidth('],
    ['h, Txt.of(placeholder))', 'h, placeholder == null ? "" : placeholder)'],
  ] },
  '1.16.1': { java: 8, packFormat: 6, srg: true, overlay: ['shims21/java', 'forge16/java'], mergeSkip: ['net.minecraft.client.gui.widget.Widget', 'net.minecraft.client.gui.components.AbstractWidget'] },
  '1.16.2': { java: 8, packFormat: 6, srg: true, overlay: ['shims21/java', 'forge16/java'], mergeSkip: ['net.minecraft.client.gui.widget.Widget', 'net.minecraft.client.gui.components.AbstractWidget'] },
  '1.16.3': { java: 8, packFormat: 6, srg: true, overlay: ['shims21/java', 'forge16/java'] },
  '1.16.4': { java: 8, packFormat: 6, srg: true, overlay: ['shims21/java', 'forge16/java'], mergeSkip: ['net.minecraft.client.gui.widget.Widget', 'net.minecraft.client.gui.components.AbstractWidget'] },
  // 1.18.2 才有 `DeferredRegister.create(ResourceKey<Registry<T>>, String)`；1.18/1.18.1（Forge 38/39）
  // 只有 `create(IForgeRegistry, String)` / `create(Class, String)` 两个重载 → 走 ForgeRegistries.ITEMS。
  '1.18': { java: 17, packFormat: 9, srg: true, patched: true, overlay: ['shims21/java', 'forge18/java'], srcReplace: [
    ['DeferredRegister.create(Registry.ITEM_REGISTRY, MOD_ID)', 'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, MOD_ID)'],
  ] },
  '1.18.1': { java: 17, packFormat: 9, srg: true, patched: true, overlay: ['shims21/java', 'forge18/java'], srcReplace: [
    ['DeferredRegister.create(Registry.ITEM_REGISTRY, MOD_ID)', 'DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.ITEMS, MOD_ID)'],
  ] },
  // 1.19 ~ 1.19.2 的注册表/创造栏还是 1.18.2 那套（Registry 而不是 BuiltInRegistries，
  // 也没有 CreativeModeTabEvent —— 那是 1.19.3 才加的），所以 base 用 forge18 而不是 forge19；
  // 但 TextComponent 在 1.19 已经删了 → 再盖一份 forge19x/Txt（Component.literal）。
  '1.19': { java: 17, packFormat: 9, srg: true, overlay: ['shims21/java', 'forge18/java', 'forge19x/java'] },
  '1.19.1': { java: 17, packFormat: 9, srg: true, overlay: ['shims21/java', 'forge18/java', 'forge19x/java'] },
  '1.19.2': { java: 17, packFormat: 9, srg: true, overlay: ['shims21/java', 'forge18/java', 'forge19x/java'] },
  // 1.19.3 和 1.19.4 只差物品渲染这一处：`renderAndDecorateItem` 在这代**没有** PoseStack 重载
  // （javap 验过：只有 (ItemStack,int,int) / (ItemStack,int,int,int) 这些），一行 srcReplace 就够。
  '1.19.3': { java: 17, packFormat: 12, srg: true, overlay: ['shims21/java', 'forge19/java'], srcReplace: [
    ['renderAndDecorateItem(pose, stack, x, y);', 'renderAndDecorateItem(stack, x, y);'],
  ] },
  // 1.20/1.20.1 同代（Forge 46），和手写的 forge-20.1 对齐：**base 是 shims201**，
  // 因为 1.20.1 的 `renderBackground` 只有单参、`mouseScrolled` 只有三参（1.20.2 才变成四参）。
  '1.20': { java: 17, packFormat: 15, srg: true, overlay: ['shims201/java', 'forge21/java'] },
};

// MC → 目标配置的「世代」参数（overlay / java / packFormat）
function eraOf(mc) {
  if (ERA[mc]) return ERA[mc];
  const v = mc.split('.').map(Number);
  const [a, b, c = 0] = v;
  if (a === 1 && b === 20 && c === 3) return { java: 17, packFormat: 18, overlay: ['shims21/java', 'forge21/java'], srg: true };
  if (a === 1 && b === 20 && c >= 5) return { java: 21, packFormat: 32, overlay: ['shims21/java', 'forge21/java'], srg: false };
  if (a === 1 && b === 21 && c <= 1) return { java: 21, packFormat: 34, overlay: ['shims21/java', 'forge21/java'], srg: false };
  if (a === 1 && b === 21 && c === 3) return { java: 21, packFormat: 42, overlay: ['shims21/java', 'forge21/java', 'forge214/java'], srg: false };
  if (a === 1 && b === 21 && c >= 11) return {
    java: 21, packFormat: 75,
    overlay: ['shims21/java', 'forge21/java', 'forge219/java'],
    srg: false,
    // 1.21.11 的双重改名：ResourceLocation → Identifier，ResourceKey#location() → identifier()，
    // 而且 Util 又从 net.minecraft.Util 搬回了 net.minecraft.util.Util。
    srcReplace: [['ResourceLocation', 'Identifier'], ['.location()', '.identifier()'], ['net.minecraft.Util;', 'net.minecraft.util.Util;']],
  };
  if (a === 1 && b === 21 && c >= 9) return { java: 21, packFormat: 69, overlay: ['shims21/java', 'forge21/java', 'forge219/java'], srg: false };
  if (a === 1 && b === 21 && c >= 5) return { java: 21, packFormat: 64, overlay: ['shims21/java', 'forge21/java', 'forge215/java'], srg: false };
  if (a === 1 && b === 20) return { java: 17, packFormat: 18, overlay: ['shims21/java', 'forge21/java'], srg: true };
  if (a === 1 && b >= 17) return { java: 17, packFormat: 13, overlay: ['shims21/java', 'forge19/java'], srg: true };
  if (a === 1 && b >= 15) return { java: 17, packFormat: 9, overlay: ['shims21/java', 'forge14/java', 'forge15/java'], srg: true };
  return { java: 17, packFormat: 8, overlay: ['shims21/java', 'forge14/java'], srg: true };
}

const PLANS = {
  modern: ['1.20.3', '1.21', '1.21.3', '1.21.5', '1.21.6', '1.21.7', '1.21.8', '1.21.9', '1.21.10', '1.21.11'],
  srg: ['1.15', '1.15.1', '1.16.1', '1.16.2', '1.16.3', '1.16.4',
    '1.18', '1.18.1', '1.19', '1.19.1', '1.19.2', '1.19.3', '1.20'],
  // 无 mojmap，走不了这条管线（2026-10-31 实测验证过）：
  //   Mojang 从 **1.14.4** 才开始发布 client_mappings，1.14.2 / 1.14.3 的 version json 里没有这个字段
  //   → mk-srg-mojmap 第一步就报「拿不到 client_mappings url」。和 1.13.2 同类（见 HANDOVER §2.1）。
  //   要覆盖它俩只能另想：拿 1.14.4 的 official↔srg 表按 srg 名反推，或直接用 1.14.4 产物实测后放宽声明。
  impossible: ['1.14.2', '1.14.3'],
};

const arg = process.argv[2];
if (arg === '--list' || !arg) {
  console.log('计划:');
  for (const [k, v] of Object.entries(PLANS)) console.log(`  ${k}: ${v.join(' ')}`);
  process.exit(0);
}
const mcs = PLANS[arg] || arg.split(',').map((s) => s.trim()).filter(Boolean);
if (!mcs) throw new Error('未知计划: ' + arg + '（可选 modern / srg / 或直接给 mc1,mc2）');

const upperPatch = (mc) => {
  const [a, b, c = 0] = mc.split('.').map(Number);
  return `${a}.${b}.${c + 1}`;
};

function log(name, text) { writeFileSync(join(LOGS, name + '.log'), text, 'utf8'); }
function lastVersionDir(since) {
  const dir = join(TESTGAME, 'versions');
  if (!existsSync(dir)) return null;
  const cands = readdirSync(dir)
    .map((n) => ({ n, t: statSync(join(dir, n)).mtimeMs }))
    .filter((x) => x.t >= since - 1000 && statSync(join(dir, x.n)).isDirectory())
    .sort((a, b) => b.t - a.t);
  return cands.length ? cands[0].n : null;
}

const results = [];
for (const mc of mcs) {
  const forge = builds[mc];
  const era = eraOf(mc);
  const key = 'forge-' + mc;
  const t0 = Date.now();
  let step = 'install';
  try {
    if (!forge) throw new Error('没有 Forge 构建版本');

    // 1) 安装（幂等：已经有实例就跳过下载，只是重新校验）
    const versionDirBefore = lastVersionDir(0);
    const inst = spawnSync(JAVA, ['-cp', PROBE_OUT, 'dev.mcver.probe.ProbeInstall',
      TESTGAME, mc, 'FORGE', forge, 'NONE', '-', '-', '1'],
      { encoding: 'utf8', timeout: 30 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
    if (inst.status !== 0) {
      log(mc, (inst.stdout || '') + (inst.stderr || ''));
      throw new Error('安装失败（exit ' + inst.status + '），详见 logs/forge-coverage/' + mc + '.log');
    }
    const instName = lastVersionDir(t0) || versionDirBefore;
    if (!instName) throw new Error('装完了但找不到版本目录');

    // 2) SRG 世代：先把 SRG 名转成 mojmap
    step = 'srg';
    if (era.srg) {
      const args = [join(ROOT, 'scripts', 'mk-srg-mojmap.mjs'), mc, '--forge', forge];
      // `--patched`：拿 Forge 打过补丁的 client jar 当输入。需要它的唯一理由是
      // 「Forge 的补丁才让 Item/Block 实现 IForgeRegistryEntry」—— 没有它，DeferredRegister<Item>
      // 的上界在编译期就过不去。1.18.2（Forge 40）删掉了这个接口，所以那之后不用；
      // 1.18/1.18.1（Forge 38/39）还必须开着（2026-10-31 实测：不开就是「类型参数 Item 不在范围中」）。
      const patched = era.patched ?? (mc.split('.').map(Number)[1] <= 17);
      if (patched) args.push('--patched');
      // 没有官方映射的世代（1.14.2/1.14.3）：借同代有映射版本的 tiny 表
      if (era.tinysFrom) args.push('--tinys-from', era.tinysFrom);
      const r = spawnSync(process.execPath, args, {
        encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024,
        env: era.mergeSkip ? { ...process.env, MCVER_SKIP_MERGE: era.mergeSkip.join(',') } : process.env,
      });
      if (r.status !== 0) {
        log(mc, (r.stdout || '') + (r.stderr || ''));
        throw new Error('映射生成失败，详见 logs/forge-coverage/' + mc + '.log');
      }
    }

    // 3) 编译（1.21.5+ 两种事件总线：先试新的 forge215，编不过再退回 forge214）
    step = 'build';
    const extra = JSON.parse(existsSync(EXTRA) ? readFileSync(EXTRA, 'utf8') : '{}');
    const base = {
      mc, java: era.java, instance: instName, srg: era.srg || undefined,
      loaderVer: forge,
      modsToml: 'META-INF/mods.toml',
      loaderRange: '[4,)',
      packFormat: era.packFormat,
      loaderDep: ['forge', '[' + forge.split('.')[0] + ',)'],
      mcRange: `[${mc},${upperPatch(mc)})`,
      sources: ['mod/src/main/java'],
      resources: ['mod/src/main/resources'],
      // 1.21.11 把 ResourceLocation 改名成 net.minecraft.resources.Identifier（跟 26.x 一致）
      srcReplace: era.srcReplace,
    };

    const overlays = [era.overlay];
    if (era.overlay.includes('forge215/java')) {
      // Forge 把事件总线换成 BusGroup 是 1.21.5 之后的某个补丁（实测 1.21.5 还是老 IEventBus，
      // 1.21.8 已经是新的）——与其硬记边界，不如两个都试。
      overlays.push(era.overlay.filter((o) => o !== 'forge215/java'));
    }

    let built = false;
    let lastOut = '';
    for (const ov of overlays) {
      extra[key] = { ...base, overlay: ov };
      writeFileSync(EXTRA, JSON.stringify(extra, null, 2) + '\n', 'utf8');
      const b = spawnSync(process.execPath, [join(ROOT, 'scripts', 'build-neoforge.mjs'), key],
        { encoding: 'utf8', timeout: 60 * 60 * 1000, maxBuffer: 64 * 1024 * 1024 });
      lastOut += (b.stdout || '') + (b.stderr || '');
      if (b.status === 0) { built = true; break; }
      console.log(`     重试：${ov.join(',')} 编不过，换下一种入口`);
    }
    log(mc, lastOut);
    if (!built) throw new Error('编译失败，详见 logs/forge-coverage/' + mc + '.log');

    // 4) 收进 dist
    const label = labelFor(extra[key].mcRange);
    const outJar = join(ROOT, 'modx', 'out', key, `watercraftlauncher-forge-1.0.0-mc${label}.jar`);
    if (!existsSync(outJar)) throw new Error('找不到产物: ' + outJar);
    copyFileSync(outJar, join(DIST, `watercraftlauncher-forge-1.0.0-mc${label}.jar`));
    results.push({ mc, ok: true, note: `mc${label}` });
    console.log(`OK   ${mc.padEnd(8)} Forge ${forge.padEnd(14)} -> mc${label}  (${Math.round((Date.now() - t0) / 1000)}s)`);
  } catch (e) {
    results.push({ mc, ok: false, note: `${step}: ${e.message}` });
    console.log(`FAIL ${mc.padEnd(8)} Forge ${forge || '?'}  ${step}: ${e.message}`);
  }
}

console.log('\n=== 汇总 ===');
for (const r of results) console.log(`${r.ok ? '✔' : '✘'} ${r.mc.padEnd(8)} ${r.note}`);
const bad = results.filter((r) => !r.ok).length;
console.log(`成功 ${results.length - bad} / ${results.length}`);
process.exit(bad ? 1 : 0);
