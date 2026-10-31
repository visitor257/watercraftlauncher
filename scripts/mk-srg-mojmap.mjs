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
import { execFileSync, spawnSync } from 'node:child_process';
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
const JDK = 'D:\\Program Files\\Java\\jdk-21\\bin';
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

// ---------- 输入清洗：重名方法的 jar 先把重复方法丢掉 ----------
// 有些世代的 `client-*-srg.jar` 里带着**两个同名同签名的方法**（mcp_config 的 tsrg 把两个混淆
// 方法写成同一个 srg 名，Forge 装机时就烤进 jar 了）。实测 1.16.2：ServerWorld 的
// `func_241122_a_(ServerWorld,BlockPos)V` 出现两次 → tiny-remapper 报
// `duplicate method … in inputs` 直接退出。工具源码见 scripts/java/DedupMethods.java。
const TOOLS_OUT = join(ROOT, 'modx', 'srg', '.tools');
const JAVAC = join(JDK, 'javac.exe');
// 一次性把小工具（scripts/java/*.java）编到 modx/srg/.tools
function ensureTool(cls, file) {
  if (existsSync(join(TOOLS_OUT, cls + '.class'))) return;
  mkdirSync(TOOLS_OUT, { recursive: true });
  console.log(`  编译 ${cls}（一次性）`);
  execFileSync(JAVAC, ['-cp', TR_CP, '-d', TOOLS_OUT, join(ROOT, 'scripts', 'java', file)], { stdio: 'inherit' });
}
function sanitizeJar(input) {
  ensureTool('DedupMethods', 'DedupMethods.java');
  const out = join(OUT, '.dedup-' + input.split(/[\\/]/).pop());
  execFileSync(JAVA, ['-cp', TOOLS_OUT + ';' + TR_CP, 'DedupMethods', input, out], { stdio: 'inherit' });
  return out;
}

// ---------- 定位输入 ----------
// mcp_config（obf→srg 的 tsrg）平时由 Forge 装机顺带下载；但 NeoForge 自己装的实例里**没有**它
// （实测 1.20.5：只有 client-*-srg.jar 和 Mojang 的 -mappings.txt）。缺的时候从 BMCLAPI 的 maven
// 补一份，解到 libraries 里缓存起来，之后再跑就命中本地。
async function fetchRetry(url, tries = 3) {
  let last;
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(120000) });
      if (r.ok) return r;
      last = new Error(`${url} -> HTTP ${r.status}`);
    } catch (e) { last = e; }
    // BMCLAPI 偶尔 504/连接超时，重试前等一下
    await new Promise((s) => setTimeout(s, 3000 * (i + 1)));
  }
  throw last;
}
async function ensureMcpConfig() {
  const base = join(LIBS, 'de', 'oceanlabs', 'mcp', 'mcp_config');
  mkdirSync(base, { recursive: true });
  const metaUrl = 'https://bmclapi2.bangbang93.com/maven/de/oceanlabs/mcp/mcp_config/maven-metadata.xml';
  const r = await fetchRetry(metaUrl);
  const xml = await r.text();
  const all = [...xml.matchAll(/<version>([^<]+)<\/version>/g)].map((m) => m[1]);
  // 只认正式版：`1.21.2-rc1-…` / `1.21-pre4-…` 这些里面没有 mappings 文件（实测 1.21.2-rc1 就是空手）
  const vers = all.filter((v) => (v === mc || v.startsWith(mc + '-')) && !/-(pre|rc|snapshot)/i.test(v))
    .sort()
    .reverse();
  // 有 `<mc>`（不带时间戳）的那份优先
  const exact = vers.indexOf(mc);
  if (exact > 0) { vers.splice(exact, 1); vers.unshift(mc); }
  if (!vers.length) throw new Error(`maven 上没有 ${mc} 的 mcp_config`);
  const ver = vers[0];
  const dir = join(base, ver);
  mkdirSync(dir, { recursive: true });
  const zip = join(dir, `mcp_config-${ver}.zip`);
  if (!existsSync(zip)) {
    const u = `https://bmclapi2.bangbang93.com/maven/de/oceanlabs/mcp/mcp_config/${ver}/mcp_config-${ver}.zip`;
    const zr = await fetchRetry(u);
    writeFileSync(zip, Buffer.from(await zr.arrayBuffer()));
    console.log(`  下载 mcp_config ${ver}`);
  }
  // 用 JDK 的 jar 工具解，取里面的映射文件放到这一层（和 Forge 装出来的布局一致）
  // 三种布局都见过：
  //   • `*-mappings.tsrg` / `*-mappings.txt`   —— 1.14.4 ~ 1.20.4
  //   • `config/joined.tsrg`                   —— 1.20.5 这种（tsrg2，包里带 patches/）
  //   • `*-srg2off.jar`（fields.csv/methods.csv）—— 1.21+，只给名字表
  const tmp = join(dir, '.unzip');
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(tmp, { recursive: true });
  execFileSync(JAR, ['xf', zip], { cwd: tmp });
  const allFiles = [];
  (function walk(d) {
    for (const e of readdirSync(d, { withFileTypes: true })) {
      const p = join(d, e.name);
      if (e.isDirectory()) walk(p); else allFiles.push(p);
    }
  })(tmp);
  const pick = (re, notRe) => allFiles.find((p) => re.test(p) && !(notRe && notRe.test(p)));
  const tsrg = pick(/-mappings\.(tsrg|txt)$/, /merged/) || pick(/[\\/]joined\.tsrg$/);
  if (tsrg) {
    const dest = join(dir, `mcp_config-${ver}-mappings${tsrg.endsWith('.tsrg') ? '.tsrg' : '.txt'}`);
    writeFileSync(dest, readFileSync(tsrg));
    rmSync(tmp, { recursive: true, force: true });
    return dest;
  }
  const up = pick(/[\\/]methods\.csv$/);
  const fl = pick(/[\\/]fields\.csv$/);
  if (up && fl) {
    const m = join(dir, `mcp_config-${ver}-methods.csv`);
    const f = join(dir, `mcp_config-${ver}-fields.csv`);
    writeFileSync(m, readFileSync(up));
    writeFileSync(f, readFileSync(fl));
    rmSync(tmp, { recursive: true, force: true });
    return m; // 调用方会走 CSV 分支
  }
  rmSync(tmp, { recursive: true, force: true });
  throw new Error(`mcp_config ${ver} 里既没有 mappings 也没有 fields.csv/methods.csv`);
}
async function findTsrg() {
  const base = join(LIBS, 'de', 'oceanlabs', 'mcp', 'mcp_config');
  const dirs = existsSync(base) ? readdirSync(base).filter((d) => d === mc || d.startsWith(mc + '-')) : [];
  for (const d of dirs.sort().reverse()) {
    const p = join(base, d);
    if (!statSync(p).isDirectory()) continue;
    // 1.20.4 起文件名是 -mappings.tsrg，1.20.1/1.20.2 是 -mappings.txt，内容都是 tsrg2
    const f = readdirSync(p).find((n) => /-mappings\.(tsrg|txt)$/.test(n) && !n.includes('merged'));
    if (f) return join(p, f);
  }
  return ensureMcpConfig();
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

// ---------- CSV 时代（1.20.5+）：mcp_config 不发 tsrg，只发 srg2off.jar ----------
// 里面是 fields.csv / methods.csv（列：searge,name,side,desc = srg 名 → 官方名）。
// 这些世代的 srg jar 恰好「类名已经是官方名、只有成员名是 m_/f_」，所以一张名字表就够，
// 不需要 tsrg 中转、也不需要 tiny-remapper（走 SrgRename 直接重写 jar）。
// 实证：1.21.5 / 1.21.11 的 mcp_config 目录里只有 -srg2off.jar，没有 -mappings.tsrg。
function findSrg2Off() {
  const base = join(LIBS, 'de', 'oceanlabs', 'mcp', 'mcp_config');
  if (!existsSync(base)) return null;
  for (const d of readdirSync(base).filter((x) => x === mc || x.startsWith(mc + '-')).sort().reverse()) {
    const p = join(base, d);
    if (!statSync(p).isDirectory()) continue;
    const jar = readdirSync(p).find((n) => n.endsWith('-srg2off.jar'));
    if (jar) return { jar: join(p, jar) };
    // ensureMcpConfig 从“只给 CSV”的 zip 里解出来时会落成这两个文件
    const meth = readdirSync(p).find((n) => n.endsWith('-methods.csv'));
    const fld = meth ? readdirSync(p).find((n) => n.endsWith('-fields.csv')) : null;
    if (meth && fld) return { methods: join(p, meth), fields: join(p, fld) };
  }
  return null;
}
const srg2off = findSrg2Off();
if (srg2off) {
  console.log('mcp_config 是 CSV 时代（' + (srg2off.jar || srg2off.methods).split(/[\\/]/).pop() + '）→ 走 SrgRename');
  let methodsCsv = srg2off.methods;
  let fieldsCsv = srg2off.fields;
  if (srg2off.jar) {
    const work = join(OUT, '.csv');
    rmSync(work, { recursive: true, force: true });
    mkdirSync(work, { recursive: true });
    execFileSync(JAR, ['xf', srg2off.jar], { cwd: work });
    methodsCsv = join(work, 'methods.csv');
    fieldsCsv = join(work, 'fields.csv');
  }
  ensureTool('SrgRename', 'SrgRename.java');
  const vanilla = sanitizeJar(findClientSrg());
  const clientOutCsv = join(OUT, `client-${mc}-mojmap.jar`);
  rmSync(clientOutCsv, { force: true });
  execFileSync(JAVA, ['-cp', TOOLS_OUT + ';' + TR_CP, 'SrgRename', vanilla, clientOutCsv,
    '--csv', methodsCsv, fieldsCsv], { stdio: 'inherit' });
  rmSync(vanilla, { force: true });
  if (srg2off.jar) rmSync(join(OUT, '.csv'), { recursive: true, force: true });
  console.log('OUT ' + clientOutCsv);
  process.exit(0);
}

// ---------- 借映射（`--tinys-from <ver>`）：没有官方映射的世代 ----------
// 1.14.2 / 1.14.3 那两代 Mojang 根本没发 client_mappings（1.14.4 才开始发），
// 但 **srg 名是跨补丁版本稳定的**，而这两代的 srg jar 与 1.14.4 一样是「类名用真实包名」。
// 所以直接把 1.14.4 的 srg2mojmap.tiny / mojmap2srg.tiny 搬过来用：
// 对得上的成员就被改名，对不上的保持 srg 名 —— 编译期会以「找不到符号」暴露。
// reobf 反向也用同一份（mojmap2srg.tiny），所以产物和别的 SRG 世代流程完全一致。
const tinysFrom = argOf('--tinys-from');
if (tinysFrom) {
  const srcDir = join(ROOT, 'modx', 'srg', tinysFrom);
  for (const n of ['srg2mojmap.tiny', 'mojmap2srg.tiny']) {
    const p = join(srcDir, n);
    if (!existsSync(p)) throw new Error(`借映射失败：${p} 不存在（先在 ${tinysFrom} 上跑一次正常流程）`);
    writeFileSync(join(OUT, n), readFileSync(p));
  }
  console.log(`映射表借用 ${tinysFrom} 的（srg 名跨补丁稳定）`);
  const srgTinyB = join(OUT, 'srg2mojmap.tiny');
  const clientOutB = join(OUT, `client-${mc}-mojmap.jar`);
  if (usePatched) {
    const vanillaOut = join(OUT, `.vanilla-${mc}-mojmap.jar`);
    const patchOut = join(OUT, `.patched-${mc}-mojmap.jar`);
    const vc = sanitizeJar(findClientSrg());
    remap(vc, vanillaOut, srgTinyB, 'srg', 'mojmap');
    rmSync(vc, { force: true });
    const pc = sanitizeJar(findForgeClient());
    const skipB = remapPatch(pc, patchOut, srgTinyB);
    rmSync(pc, { force: true });
    for (const c of (process.env.MCVER_SKIP_MERGE || '').split(',').map((s) => s.trim()).filter(Boolean)) {
      skipB.add(c.replace(/\./g, '/') + '.class');
    }
    mergeJars(vanillaOut, patchOut, clientOutB, skipB);
    rmSync(vanillaOut, { force: true });
    rmSync(patchOut, { force: true });
  } else {
    const c = sanitizeJar(findClientSrg());
    remap(c, clientOutB, srgTinyB, 'srg', 'mojmap');
    rmSync(c, { force: true });
  }
  // universal jar（Forge/NeoForge 的 loader 本体）也用同一份映射转一遍
  const loaderVerB = forgeVer || neoVer;
  const groupB = forgeVer ? ['net', 'minecraftforge', 'forge'] : ['net', 'neoforged', 'neoforge'];
  const uniDirB = [
    join(LIBS, ...groupB, `${mc}-${loaderVerB}`),
    join(LIBS, 'net', 'neoforged', 'neoforge', loaderVerB),
    join(LIBS, 'net', 'neoforged', 'forge', `${mc}-${loaderVerB}`),
  ].find((d) => existsSync(d) && readdirSync(d).some((f) => f.endsWith('-universal.jar')));
  if (uniDirB) {
    const uni = join(uniDirB, readdirSync(uniDirB).find((f) => f.endsWith('-universal.jar')));
    const uniOut = join(OUT, `universal-${loaderVerB}-mojmap.jar`);
    remap(uni, uniOut, srgTinyB, 'srg', 'mojmap', [clientOutB]);
    console.log('OUT ' + uniOut);
  } else {
    console.log('  [warn] 没找到 universal jar，跳过');
  }
  console.log('OUT ' + clientOutB);
  console.log('OUT ' + srgTinyB);
  console.log('OUT ' + join(OUT, 'mojmap2srg.tiny'));
  process.exit(0);
}

const tsrgPath = await findTsrg();
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
  for (const [a, b] of clsPairs) if (!byClass.has(a)) byClass.set(a, { dst: b, mem: [], seen: new Set() });
  for (const [cls, kind, srcName, dstName, srcDesc] of memPairs) {
    const rec = byClass.get(cls);
    if (!rec) continue;
    // 同一个类里「两个成员映射到同一个名字」必须提前剔掉，否则 tiny-remapper 直接
    // `duplicate method … in inputs` 退出。实测 mcp_config 1.16.2 的 tsrg 就有这种坏数据：
    // `aag`（= ServerWorld）里 `a` 和 `b` 两个 (Laag;Lfx;)V 都写成 func_241122_a_。
    // 保留第一条，后面的丢掉 —— 丢掉的成员保持 srg 名，类里不会出现重名成员。
    const key = `${kind}|${srcDesc}|${dstName}`;
    if (rec.seen.has(key)) continue;
    rec.seen.add(key);
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
function remap(input, output, mappings, from, to, extraCp = [], flags = []) {
  console.log(`  remap ${input.split(/[\\/]/).pop()} ${from} -> ${to}`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', input, output, mappings, from, to, ...extraCp, ...flags], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
}

// 补丁包 remap：冲突时丢掉「撞名的那条映射」再重试，并把冲突类**整类排除出合并**。
// 冲突从哪来：Forge 会往原版类里**手写**成员，而原版同签名成员映射过去刚好同名。
// 实测 1.16.1/1.16.2/1.16.4 的 `AbstractWidget`：Forge 加了 `getHeight()I`，原版 `func_238483_d_()I`
// 映射过去也叫 getHeight → tiny-remapper 判「unfixable conflicts」直接退出（`--ignoreConflicts`
// 管不了这一类：同一个类里两个成员落到同一个名字）。
//
// 只丢映射不够：那样合并出来的类里会**同时**有 getHeight 和 func_238483_d_，回头 reobf（mojmap→srg）
// 又会撞一次（实测 1.16.1 卡在 build-neoforge 的 reobf 那一步）。所以再进一步：
// 这些类**不参与覆盖**（保留原版 remap 出来的那份），Forge 手写的那个 getHeight 就进不了 classpath。
// 代价是我们看不到 Forge 给这个类加的便利方法 —— 我们没用到，运行时那些方法本来也在。
function remapPatch(input, output, tinyPath) {
  const run = (mp) => spawnSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', input, output, mp, 'srg', 'mojmap',
    '--ignoreConflicts'], { encoding: 'utf8', maxBuffer: 1 << 28 });
  console.log(`  remap ${input.split(/[\\/]/).pop()} srg -> mojmap`);
  // 一次冲突修完往往还会冒出第二个（丢了一条映射后传播关系变了），所以循环修到通为止
  const badClasses = new Set(); // 冲突类的**源**名（srg 侧）
  const droppedByClass = new Map(); // 类 -> Set(要放弃改名的**目标名**)
  const origLines = readFileSync(tinyPath, 'utf8').split('\n');
  // 类的「源名 → 目标名」对照：mergeJars 是按**输出 jar 里的路径**跳过的，
  // 而冲突报告里给的是源名。1.14.x/1.16.x 这两代类名真的会改（AbstractMinecartEntity → AbstractMinecart），
  // 不翻译就会「跳过了但没跳过」，合并出来一个类里同时有 func_xxx 和 getXxx（实测 1.14.2 卡在这）。
  const classRename = new Map();
  for (const line of origLines) {
    if (!line.startsWith('c\t')) continue;
    const f = line.split('\t');
    if (f[1] && f[2]) classRename.set(f[1], f[2]);
  }
  const postName = (c) => classRename.get(c) || c;
  for (let round = 1; round <= 6; round++) {
    // 按已累计的丢弃表过滤映射
    let cur = null;
    const lines = [];
    for (const line of origLines) {
      if (line.startsWith('c\t')) { cur = line.split('\t')[1]; lines.push(line); continue; }
      if (line.startsWith('\t') && cur && droppedByClass.has(cur)) {
        const f = line.split('\t'); // ['', kind, desc, src, dst]
        if (droppedByClass.get(cur).has(f[4])) continue; // 目标名撞车的那条不要
      }
      lines.push(line);
    }
    const mp = droppedByClass.size ? join(OUT, '.srg2mojmap-noconflict.tiny') : tinyPath;
    if (droppedByClass.size) writeFileSync(mp, lines.join('\n'), 'utf8');
    const r = run(mp);
    if (r.status === 0) return new Set([...badClasses].map((c) => postName(c) + '.class'));
    const text = (r.stdout || '') + (r.stderr || '');
    let fresh = 0;
    // 行格式：`[WARN]   METHODs net/minecraft/…/AbstractWidget/[getHeight, func_238483_d_]()I -> getHeight`
    // 方括号里的名字可能是**别的类**限定过的（`…/FurnaceMinecartEntity/func_174898_m`）——
    // 那是「子类的映射传播到父类、和父类已有的同名成员撞车」，要连子类那条一起丢。
    for (const m of text.matchAll(/\b(METHODs|FIELDs) (\S+)\/\[([^\]]+)\](\S*) -> (\S+)/g)) {
      const [, , cls, names, , dst] = m;
      const addDrop = (c) => {
        badClasses.add(c);
        if (!droppedByClass.has(c)) droppedByClass.set(c, new Set());
        if (!droppedByClass.get(c).has(dst)) { droppedByClass.get(c).add(dst); fresh++; }
      };
      addDrop(cls);
      for (const raw of names.split(',').map((s) => s.trim())) {
        if (raw.includes('/')) addDrop(raw.slice(0, raw.lastIndexOf('/')));
      }
    }
    if (!fresh) {
      process.stdout.write(text);
      throw new Error(`补丁包 remap 第 ${round} 轮仍失败，且没解析出新的冲突`);
    }
    console.log(`  第 ${round} 轮冲突：放弃 ${[...droppedByClass.keys()].map((c) => c.split('/').pop()).join(', ')} 的这些改名`);
  }
  throw new Error('补丁包 remap 反复冲突，放弃');
}

// 把 overlay 盖到 base 上（overlay 优先），用 JDK 自带的 jar 工具解/打，不引第三方 zip 库。
// skip：相对路径集合（如 `net/minecraft/.../AbstractWidget.class`），这些只保留 base 的版本。
function mergeJars(baseJar, overlayJar, outJar, skip = new Set()) {
  console.log(`  merge ${baseJar.split(/[\\/]/).pop()} <- ${overlayJar.split(/[\\/]/).pop()}${skip.size ? `（跳过 ${skip.size} 个类）` : ''}`);
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
        if (skip.size) {
          const rel = s.slice(b.length + 1).replace(/\\/g, '/');
          if (skip.has(rel)) continue;
        }
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
  const vanillaClean = sanitizeJar(vanillaJar);
  remap(vanillaClean, vanillaOut, srgTiny, 'srg', 'mojmap');
  rmSync(vanillaClean, { force: true });
  // 补丁包单独走 remapPatch：它有冲突自愈（见上面的注释），并把冲突类排除出合并
  const patchClean = sanitizeJar(clientSrg);
  const skip = remapPatch(patchClean, patchOut, srgTiny);
  rmSync(patchClean, { force: true });
  // 自愈只能看到「本类里两个成员撞名」；还有一种冲突是**继承下来**的：
  // 父类被改名，子类自己也声明了同名成员（1.16.x 的 Widget → AbstractWidget 就是这种）。
  // 那种要由调用方点名，走环境变量传进来（build-forge-coverage 的 era.mergeSkip）。
  for (const c of (process.env.MCVER_SKIP_MERGE || '').split(',').map((s) => s.trim()).filter(Boolean)) {
    skip.add(c.replace(/\./g, '/') + '.class');
  }
  mergeJars(vanillaOut, patchOut, clientOut, skip);
  rmSync(vanillaOut, { force: true });
  rmSync(patchOut, { force: true });
} else {
  const clean = sanitizeJar(clientSrg);
  remap(clean, clientOut, srgTiny, 'srg', 'mojmap');
  rmSync(clean, { force: true });
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
