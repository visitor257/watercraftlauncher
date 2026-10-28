// SRG 时代（Forge ≤ 1.20.4 / NeoForge ≤ 20.4）的编译前置：
// 把 SRG 名的 client / universal jar 转成 **mojmap（官方名）**，好让主源码直接 javac；
// 同时生成反向映射 mojmap -> srg，编完再把我们的 jar reobf 回 SRG。
//
//   node scripts/mk-srg-mojmap.mjs 1.20.4 --forge 49.2.13
//   node scripts/mk-srg-mojmap.mjs 1.20.1 --neo 47.1.84   （NeoForge 20.1 == Forge 47 分支）
//   node scripts/mk-srg-mojmap.mjs 1.17.1 --forge 37.1.1 --patched
//
// --patched：改用 Forge 的 *-client.jar（**打过补丁**的那份）当输入，而不是 vanilla 的
// client-*-srg.jar。差别很关键：Forge 的补丁才让 Item extends ForgeRegistryEntry，
// 少了它，DeferredRegister<Item> 那个 IForgeRegistryEntry 上界在编译期就编不过
// （运行时是 ModLauncher 现场打补丁，所以能跑）。1.16.5/1.17.1 必须开着。
//
// 映射来源（两份拼起来，正是 ForgeGradle 内部做的事）：
//   de.oceanlabs.mcp:mcp_config:<mc>/*-mappings.tsrg   tsrg2：obf -> srg
//   Mojang version json 的 downloads.client_mappings   proguard：左=官方名，右=混淆名
// 用 obf 做桥梁就能算出 srg -> official。
//
// 两个必须记住的格式事实（都在这里踩过）：
//   ① tsrg2 里「srg 类名」是 `net/minecraft/src/C_182965_` 这种类 id，而运行时 client-*-srg.jar
//      的类名是**官方包名**，只有方法/字段还叫 `m_/f_` → 类映射恒等，只换成员名。
//   ② Mojang txt 是 `官方名 -> 混淆名`（类、字段、方法一律如此）。反过来读会几乎全不匹配。
//
// 产物（默认 modx/srg/<mc>/）：
//   client-<mc>-mojmap.jar
//   universal-<loaderVer>-mojmap.jar
//   srg2mojmap.tiny / mojmap2srg.tiny
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const argv = process.argv.slice(2);
const mc = argv[0];
const argOf = (k) => {
  const i = argv.indexOf(k);
  return i >= 0 ? argv[i + 1] : null;
};
const forgeVer = argOf('--forge');
const usePatched = argv.includes('--patched');
const neoVer = argOf('--neo');
if (!mc || (!forgeVer && !neoVer)) {
  console.error('用法: node scripts/mk-srg-mojmap.mjs <mc> --forge <forgeVer> | --neo <neoVer>');
  process.exit(2);
}

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const LIBS = join(GAME, 'libraries');
const OUT = join(ROOT, 'modx', 'srg', mc);
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';
const JAVA = join(JDK, 'java.exe');
const JAR = join(JDK, 'jar.exe');

mkdirSync(OUT, { recursive: true });

// ---------- tiny-remapper 运行时 classpath（复用 Gradle 缓存里已有的 jar） ----------
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

// ---------- 定位输入 ----------
function findTsrg() {
  const base = join(LIBS, 'de', 'oceanlabs', 'mcp', 'mcp_config');
  const dirs = readdirSync(base).filter((d) => d === mc || d.startsWith(mc + '-')).sort().reverse();
  for (const d of dirs) {
    const p = join(base, d);
    if (!statSync(p).isDirectory()) continue;
    // 1.20.4 起文件名是 -mappings.tsrg，1.20.1/1.20.2 是 -mappings.txt，内容都是 tsrg2
    const f = readdirSync(p).find((n) => /-mappings\.(tsrg|txt)$/.test(n) && !n.includes('merged'));
    if (f) return join(p, f);
  }
  throw new Error(`libraries 里没有 ${mc} 的 mcp_config mappings.tsrg`);
}
function findClientSrg() {
  const base = join(LIBS, 'net', 'minecraft', 'client');
  const dirs = readdirSync(base).filter((d) => d.startsWith(mc + '-')).sort().reverse();
  for (const d of dirs) {
    const f = readdirSync(join(base, d)).find((n) => n.endsWith('-srg.jar'));
    if (f) return join(base, d, f);
  }
  throw new Error(`libraries 里没有 ${mc} 的 client-*-srg.jar`);
}
function findForgeClient() {
  const base = join(LIBS, 'net', 'minecraftforge', 'forge', `${mc}-${forgeVer}`);
  const f = `forge-${mc}-${forgeVer}-client.jar`;
  const p = join(base, f);
  if (!existsSync(p)) throw new Error(`找不到 ${p}（--patched 需要 Forge 已装好）`);
  return p;
}
async function clientMappings() {
  const cache = join(OUT, `client-mappings-${mc}.txt`);
  if (existsSync(cache) && statSync(cache).size > 100000) return cache;
  const vjson = join(GAME, 'versions', mc, mc + '.json');
  let url = null;
  if (existsSync(vjson)) {
    const j = JSON.parse(readFileSync(vjson, 'utf8'));
    url = j.downloads && j.downloads.client_mappings && j.downloads.client_mappings.url;
  }
  if (!url) {
    // 本地那份原版 json 有时被裁剪过（没有 downloads 段）→ 找 BMCLAPI 要一份完整的
    for (const u of [`https://bmclapi2.bangbang93.com/version/${mc}/json`]) {
      try {
        const r0 = await fetch(u);
        if (!r0.ok) continue;
        const j0 = await r0.json();
        url = j0.downloads && j0.downloads.client_mappings && j0.downloads.client_mappings.url;
        if (url) break;
      } catch { /* 下一个源 */ }
    }
  }
  if (!url) throw new Error(`拿不到 ${mc} 的 client_mappings url（versions/${mc}/${mc}.json 缺字段）`);
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url} -> HTTP ${r.status}`);
  writeFileSync(cache, Buffer.from(await r.arrayBuffer()));
  console.log(`  下载 client mappings (${Math.round(statSync(cache).size / 1024)}KB)`);
  return cache;
}

// ---------- 描述符工具 ----------
const PRIM = { int: 'I', long: 'J', float: 'F', double: 'D', boolean: 'Z', byte: 'B', char: 'C', short: 'S', void: 'V' };
function typeToDesc(t) {
  t = (t || '').trim();
  if (!t) return 'V';
  if (t.endsWith('[]')) return '[' + typeToDesc(t.slice(0, -2));
  return PRIM[t] || 'L' + t.replace(/\./g, '/') + ';';
}
// proguard 的「(A,B)Ret」→ JVM 描述符
function pgDesc(sig) {
  const i = sig.indexOf('?');
  const args = sig.slice(0, i).slice(1, -1);
  const ret = sig.slice(i + 1);
  const a = args.trim() ? args.split(',').map(typeToDesc).join('') : '';
  return '(' + a + ')' + typeToDesc(ret);
}
function mapDesc(desc, map) {
  return desc.replace(/L([^;]+);/g, (all, cls) => {
    const to = map.get(cls);
    return to ? 'L' + to + ';' : all;
  });
}

// ---------- tsrg：obf -> srg（两个世代的格式不一样，都要吃） ----------
// **tsrg2**（1.17+）：
//   <obfClass> <srgClass> <id>
//   \t<obfName> <srgName> <id>              字段
//   \t<obfName> <desc> <srgName> <id>       方法（desc 是 obf 类名命名空间）
//   \t\t<idx> <obfVar> <srgVar> <id>        局部变量（跳过）
// **tsrg v1**（≤1.16.5，无文件头）：
//   <obfClass> <srgClass>
//   \t<obfName> <srgName>                   字段
//   \t<obfName> <desc> <srgName>            方法
// 判别式只看文件头 `tsrg2` —— 1.16.5 那份第一行直接就是类映射（`a net/minecraft/...`），
// 以前按 v2 解析（要求 3 列）会把**每一行都当垃圾扔掉**，最后映射表只剩一个 20 字节的文件头，
// 「mojmap jar」其实是 SRG jar 原件 —— 这就是当初误判成「1.16.5 官方映射残缺」的根因。
function parseTsrg(path) {
  const obf2srgClass = new Map();
  const srg2obfClass = new Map();
  const meta = new Map();
  let cur = null;
  const lines = readFileSync(path, 'utf8').split(/\r?\n/);
  const isV2 = lines.some((l) => l.startsWith('tsrg2'));
  for (const raw of lines) {
    if (!raw || raw.startsWith('tsrg2') || raw.startsWith('#')) continue;
    if (!raw.startsWith('\t')) {
      const tk = raw.trim().split(/\s+/);
      if (tk.length < 2) continue;
      obf2srgClass.set(tk[0], tk[1]);
      if (!srg2obfClass.has(tk[1])) srg2obfClass.set(tk[1], tk[0]);
      cur = { fields: [], methods: [] };
      meta.set(tk[0], cur);
    } else if (raw.startsWith('\t\t')) {
      continue; // 局部变量（只有 v2 有）
    } else if (cur) {
      const tk = raw.trim().split(/\s+/);
      if (isV2) {
        // v2 的 srg 名在第 3 列、方法描述符在第 2 列
        if (tk.length === 4) { if (tk[1] !== '()V' || tk[2] !== '<clinit>') cur.methods.push([tk[0], tk[2], tk[1]]); }
        else if (tk.length === 3 && tk[1] !== 'static') cur.fields.push([tk[0], tk[1]]);
      } else {
        // v1：`obf srg` 或 `obf (desc) srg`；字段的 srg 名不含括号
        if (tk.length === 3) { if (tk[2] !== '<clinit>') cur.methods.push([tk[0], tk[2], tk[1]]); }
        else if (tk.length === 2 && tk[1] !== 'static') cur.fields.push([tk[0], tk[1]]);
      }
    }
  }
  return { obf2srgClass, srg2obfClass, meta, isV2 };
}

// ---------- Mojang proguard：左官方名 / 右混淆名 ----------
function parseMojmap(path) {
  const obf2mojClass = new Map();
  const meta = new Map(); // obfClass -> { fields: Map(obfName -> {official, type}), methods: Map(obfName+desc -> official) }
  let cur = null;
  for (const raw of readFileSync(path, 'utf8').split(/\r?\n/)) {
    if (!raw) continue;
    if (!raw.startsWith(' ')) {
      const m = /^(?:\d+(?::\d+)*:)?(\S+) -> (\S+):$/.exec(raw.trim());
      if (!m) continue;
      const official = m[1].replace(/\./g, '/');
      const obf = m[2];
      obf2mojClass.set(obf, official);
      cur = { fields: new Map(), methods: new Map() };
      meta.set(obf, cur);
      continue;
    }
    if (!cur) continue;
    const line = raw.trim().replace(/^\d+(?::\d+)*:/, '');
    const arrow = line.indexOf(' -> ');
    if (arrow < 0) continue;
    const left = line.slice(0, arrow).trim(); // 官方名那头
    const obfName = line.slice(arrow + 4).trim();
    if (!obfName) continue;
    const paren = left.indexOf('(');
    if (paren >= 0) {
      const head = left.slice(0, paren).split(/\s+/);
      const official = head.pop();
      const ret = head.join(' ');
      // key 里的类型先换成 `/`（tsrg 那边是 obf 名、换出来也是 `/`）——不换的话
      // 参数只要带自定义类就两边对不上，方法覆盖率会卡在 34%。
      // key 统一用 JVM 描述符（tsrg 那边就是 JVM 描述符，只是类名是 obf）。
      // 用 proguard 的 `(int)?Ret` 当 key 会和 tsrg 的 `(I)Lxxx;` 对不上。
      const sig = pgDesc('(' + left.slice(paren + 1, -1) + ')?' + ret);
      cur.methods.set(obfName + '\u0000' + sig, { official, ret, args: left.slice(paren + 1, -1) });
    } else {
      const parts = left.split(/\s+/);
      const official = parts.pop();
      cur.fields.set(obfName, { official, type: parts.join(' ') });
    }
  }
  return { obf2mojClass, meta };
}

const tsrgPath = findTsrg();
const clientSrg = usePatched ? findForgeClient() : findClientSrg();
const mojPath = await clientMappings();
console.log(`tsrg:   ${tsrgPath}`);
console.log(`client: ${clientSrg}`);

const { obf2srgClass, srg2obfClass, meta: srgMeta, isV2 } = parseTsrg(tsrgPath);
const { obf2mojClass, meta: mojMeta } = parseMojmap(mojPath);
console.log(`STATS tsrg=${obf2srgClass.size} (${isV2 ? 'v2' : 'v1'}) mojmap=${obf2mojClass.size}`);

// srg 源命名空间怎么取：
//   **tsrg2（1.17+）**：srg 类名是 `net/minecraft/src/C_182965_` 这种**类 id**，而运行时
//   `client-*-srg.jar` 里的类名是**官方包名** → 类映射只能恒等（早期就踩过，错在把 id 当类名）。
//   **tsrg v1（≤1.16.5）**：srg 类名就是 jar 里的真实包名（`gui/FontRenderer`、`gui/AbstractGui`），
//   而 mojmap 那边叫 `Font` / `Gui` → **必须真改名**，否则源码引 `Font` 编不过。
// 所以按世代选「源命名空间」：v2 用 mojmap 名当源（恒等），v1 用 srg 名当源（真改）。
const SRG_ID = /^net\/minecraft\/src\/C_\d+_$/;
const classRenames = isV2 ? 0 : [...obf2srgClass.values()].filter((c) => !SRG_ID.test(c)).length;
if (isV2 && classRenames > 0) console.log('WARN tsrg2 里有非 C_ id 的 srg 类名，按恒等处理');
const obf2srcClass = new Map();
for (const [obf, srg] of obf2srgClass) {
  const moj = obf2mojClass.get(obf);
  if (!moj) continue;
  obf2srcClass.set(obf, isV2 ? moj : srg);
}
// 官方类名 -> 源命名空间类名（字段描述符在 Mojang txt 里是官方名，tiny 要源命名空间）
const official2src = new Map();
for (const [obf, src] of obf2srcClass) {
  const moj = obf2mojClass.get(obf);
  if (moj && !official2src.has(moj)) official2src.set(moj, src);
}
function descToSrc(desc) {
  return desc.replace(/L([^;]+);/g, (all, cls) => {
    const to = official2src.get(cls);
    return to ? 'L' + to + ';' : all;
  });
}

const classes = []; // [srcClass, mojClass]
const members = []; // [srcClass, kind, srcName, mojName, srcDesc]
// 反向（reobf 用）：类对换顺序，**描述符也必须换回 mojmap 命名空间**。
// 1.17+ 类映射恒等，所以以前只倒名字也能跑；1.16.5 真改类名了，倒错了就 reobf 不到
// （运行时 NoClassDefFoundError: net/minecraft/world/item/Item —— 跑了整整一轮才看出来）。
const classesRev = []; // [mojClass, srcClass]
const membersRev = []; // [mojClass, kind, mojName, srcName, mojDesc]
let noClass = 0;
let fieldHits = 0;
let methodHits = 0;
let methodMiss = 0;
for (const [obfClass, info] of srgMeta) {
  const mojClass = obf2mojClass.get(obfClass);
  const mm = mojMeta.get(obfClass);
  const srcClass = obf2srcClass.get(obfClass);
  if (!mojClass || !mm || !srcClass) { noClass++; continue; }
  classes.push([srcClass, mojClass]);
  classesRev.push([mojClass, srcClass]);
  for (const [obfName, srgName] of info.fields) {
    const rec = mm.fields.get(obfName);
    if (!rec || !rec.official || rec.official === srgName) continue;
    const mojDesc = typeToDesc(rec.type);
    members.push([srcClass, 'f', srgName, rec.official, descToSrc(mojDesc)]);
    membersRev.push([mojClass, 'f', rec.official, srgName, mojDesc]);
    fieldHits++;
  }
  for (const [obfName, srgName, srgDesc] of info.methods) {
    // tsrg 里方法描述符用 **obf 类名**（`(Lcuk;Lhx;)Z`）。
    // JOIN 的 key 用 mojmap 命名空间；写进 tiny 的正向描述符要 **srg 命名空间**。
    const mojDesc = mapDesc(srgDesc, obf2mojClass);
    const srcDesc = mapDesc(srgDesc, obf2srcClass);
    // 严格按「obf 名 + 完整 JVM 描述符」JOIN。不做「名字唯一就当它」的兜底：
    // obf 同名不同描述符的重载很常见，兜底会把 stacksTo 错配成 durability（真踩过）。
    const rec = mm.methods.get(obfName + '\u0000' + mojDesc);
    if (!rec) { methodMiss++; continue; }
    if (!rec.official || rec.official === srgName) continue;
    members.push([srcClass, 'm', srgName, rec.official, srcDesc]);
    membersRev.push([mojClass, 'm', rec.official, srgName, mojDesc]);
    methodHits++;
  }
}
console.log(`STATS classes=${classes.length} skip=${noClass} fields=${fieldHits} methods=${methodHits} miss=${methodMiss} total=${members.length} renamed=${classes.filter(([a, b]) => a !== b).length}`);

// tiny v2：成员行是「描述符在前」（`m <desc> <src> <dst>`）。
// 试过 desc 在末尾会被 mapping-io 当成名字列 → missing name columns（字段名只有一两个字符时必现）。
function writeTiny(path, from, to, clsPairs, memPairs) {
  const lines = [`tiny\t2\t0\t${from}\t${to}`];
  const byClass = new Map();
  for (const [a, b] of clsPairs) if (!byClass.has(a)) byClass.set(a, { dst: b, mem: [] });
  for (const [cls, kind, srcName, dstName, srcDesc] of memPairs) {
    const rec = byClass.get(cls);
    if (!rec) continue;
    rec.mem.push(`\t${kind}\t${srcDesc}\t${srcName}\t${dstName}`);
  }
  for (const [src, rec] of byClass) {
    lines.push(`c\t${src}\t${rec.dst}`);
    for (const l of rec.mem) lines.push(l);
  }
  writeFileSync(path, lines.join('\n') + '\n', 'utf8');
}

const srgTiny = join(OUT, 'srg2mojmap.tiny');
const mojTiny = join(OUT, 'mojmap2srg.tiny');
writeTiny(srgTiny, 'srg', 'mojmap', classes, members);
writeTiny(mojTiny, 'mojmap', 'srg', classesRev, membersRev);

// ---------- remap jar ----------
function remap(input, output, mappings, from, to, extraCp = []) {
  console.log(`  remap ${input.split(/[\\/]/).pop()} ${from} -> ${to}`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', input, output, mappings, from, to, ...extraCp], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
}

// 把 overlay 盖到 base 上（overlay 优先），用 JDK 自带的 jar 工具解/打，不引第三方 zip 库。
function mergeJars(baseJar, overlayJar, outJar) {
  console.log(`  merge ${baseJar.split(/[\\/]/).pop()} <- ${overlayJar.split(/[\\/]/).pop()}`);
  const work = join(OUT, '.merge');
  rmSync(work, { recursive: true, force: true });
  const a = join(work, 'base');
  const b = join(work, 'over');
  mkdirSync(a, { recursive: true });
  mkdirSync(b, { recursive: true });
  execFileSync(JAR, ['xf', baseJar], { cwd: a });
  execFileSync(JAR, ['xf', overlayJar], { cwd: b });
  // 目录递归拷，同路径以 overlay 覆盖；MANIFEST 以 base 为准（overlay 的是补丁包自己的）
  const copyTree = (from, to) => {
    for (const e of readdirSync(from, { withFileTypes: true })) {
      const s = join(from, e.name);
      const d = join(to, e.name);
      if (e.isDirectory()) {
        mkdirSync(d, { recursive: true });
        copyTree(s, d);
      } else {
        if (e.name === 'MANIFEST.MF' && to.endsWith(join('META-INF'))) continue;
        writeFileSync(d, readFileSync(s));
      }
    }
  };
  copyTree(b, a);
  rmSync(outJar, { force: true });
  execFileSync(JAR, ['cf', outJar, '-C', a, '.'], { stdio: 'inherit' });
  rmSync(work, { recursive: true, force: true });
}

const clientOut = join(OUT, `client-${mc}-mojmap.jar`);
if (usePatched) {
  // Forge 的 `-client.jar` 世代不同：
  //   • 1.17.1（Forge 37）是全量 jar（含整个游戏 + 补丁）
  //   • **1.16.5（Forge 36）只是增量补丁包 —— 1275 个类，连 Font/Button/Minecraft 之外的基础类都缺**
  // 所以 --patched 统一走「原版 srg jar remap 出全量 + Forge 补丁包 remap 出覆盖层 + 合并」，
  // 否则编译期会满屏「找不到符号 Font」——而且 jar 里有 Minecraft 却什么都没有，很难看出原因。
  const vanillaJar = findClientSrg();
  const vanillaOut = join(OUT, `.vanilla-${mc}-mojmap.jar`);
  const patchOut = join(OUT, `.patched-${mc}-mojmap.jar`);
  remap(vanillaJar, vanillaOut, srgTiny, 'srg', 'mojmap');
  remap(clientSrg, patchOut, srgTiny, 'srg', 'mojmap');
  mergeJars(vanillaOut, patchOut, clientOut);
  rmSync(vanillaOut, { force: true });
  rmSync(patchOut, { force: true });
} else {
  remap(clientSrg, clientOut, srgTiny, 'srg', 'mojmap');
}

const loaderVer = forgeVer || neoVer;
const loaderGroup = forgeVer ? 'net/minecraftforge/forge' : 'net/neoforged/neoforge';
// 目录布局各代不同：Forge 是 net/minecraftforge/forge/<mc>-<ver>，
// NeoForge 20.2+ 是 net/neoforged/neoforge/<ver>，NeoForge 20.1 是 net/neoforged/forge/<mc>-<ver>。
const uniCandidates = [
  join(LIBS, ...loaderGroup.split('/'), `${mc}-${loaderVer}`),
  join(LIBS, 'net', 'neoforged', 'neoforge', loaderVer),
  join(LIBS, 'net', 'neoforged', 'forge', `${mc}-${loaderVer}`),
];
let universal = null;
for (const uniDir of uniCandidates) {
  if (!existsSync(uniDir)) continue;
  const f = readdirSync(uniDir).find((n) => n.endsWith('-universal.jar'));
  if (f) { universal = join(uniDir, f); break; }
}
let universalOut = null;
if (universal) {
  universalOut = join(OUT, `universal-${loaderVer}-mojmap.jar`);
  remap(universal, universalOut, srgTiny, 'srg', 'mojmap', [clientOut]);
} else {
  console.log('  [warn] 没找到 universal jar，跳过');
}

console.log('OUT ' + clientOut);
if (universalOut) console.log('OUT ' + universalOut);
console.log('OUT ' + srgTiny);
console.log('OUT ' + mojTiny);
