// 1.12.2（以及 1.7.10 / 1.10.2 同一代）的 remap 前置：原版 obf jar -> **SRG** jar。
//
// 这一代没有 Mojang 官方映射（官方 client_mappings 是 1.14.4 才开始发的），Forge 的
// 编译/运行时命名空间是 **SRG**：类名已经是真包名（`net/minecraft/client/gui/GuiScreen`），
// 成员名是 `func_xxxxx` / `field_xxxxx`。所以：
//
//   mcp_config 里的 `config/joined.tsrg`（tsrg v1：`obf srg`）就是我们要的全部映射，
//   一次 remap 到位，**不需要再 reobf** —— 编出来的字节码引用就是运行期名字。
//
// 和 1.16.5 那条路的关键区别：
//   * 1.16.5 走「mojmap 编 → reobf 回 SRG」；
//   * 1.12.2 直接「SRG 编 → 不 reobf」。少一跳就少一类错。
//
// 用法： node scripts/mk-mcp-srg.mjs [--mc 1.12.2] [--cfg 1.12.2-20200226.224830]
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const OUT = join(ROOT, 'modx', 'srg');
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';
const JAVA = join(JDK, 'java.exe');

function arg(name, dflt) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}
const MC = arg('mc', '1.12.2');
const CFG = arg('cfg', '1.12.2-20200226.224830');

// tiny-remapper 运行时（复用 Gradle 缓存里已有的 jar，不下新东西）
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

// ---------- 最小 class 文件解析（只取 this_class + 字段名/描述符） ----------
//
// 用不着 ASM：tiny v2 的字段行要描述符，而 joined.tsrg 只给名字对，所以得去 jar 里读。
// 只跳过常量池、接口表和属性，不碰 Code，二十几行就够。
function parseClassFields(buf) {
  const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let p = 0;
  const u1 = () => buf[p++];
  const u2 = () => { const v = dv.getUint16(p); p += 2; return v; };
  const u4 = () => { const v = dv.getUint32(p); p += 4; return v; };
  if (u4() !== 0xcafebabe) throw new Error('bad magic');
  u2(); u2(); // minor / major
  const cp = u2();
  const utf8 = new Array(cp);
  const clsNameIdx = new Array(cp);
  for (let i = 1; i < cp; i++) {
    const tag = u1();
    switch (tag) {
      case 1: { const len = u2(); utf8[i] = buf.toString('utf8', p, p + len); p += len; break; }
      case 7: clsNameIdx[i] = u2(); break;
      case 8: case 16: case 19: case 20: u2(); break;
      case 15: u1(); u2(); break;
      case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: u4(); break;
      case 5: case 6: u4(); u4(); i++; break;
      default: throw new Error('未知常量池 tag ' + tag);
    }
  }
  u2(); // access
  const thisClass = u2();
  u2(); // super
  const ifCount = u2();
  p += ifCount * 2;
  const fields = [];
  const fieldCount = u2();
  for (let i = 0; i < fieldCount; i++) {
    u2(); // access
    const name = utf8[u2()];
    const desc = utf8[u2()];
    const attrs = u2();
    for (let a = 0; a < attrs; a++) { u2(); const len = u4(); p += len; }
    fields.push([name, desc]);
  }
  return { name: utf8[clsNameIdx[thisClass]], fields };
}

function readFieldDescs(jarPath, workDir) {
  const tmp = join(workDir, '.vanilla');
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(tmp, { recursive: true });
  execFileSync(join(JDK, 'jar.exe'), ['xf', jarPath], { cwd: tmp, stdio: 'inherit' });
  const out = new Map();
  (function walk(dir) {
    for (const e of readdirSync(dir, { withFileTypes: true })) {
      const p = join(dir, e.name);
      if (e.isDirectory()) { walk(p); continue; }
      if (!p.endsWith('.class')) continue;
      const { name, fields } = parseClassFields(readFileSync(p));
      out.set(name, new Map(fields));
    }
  })(tmp);
  rmSync(tmp, { recursive: true, force: true });
  return out;
}

// ---------- 1) 从 mcp_config zip 里取 joined.tsrg ----------
const cfgZip = join(GAME, 'libraries', 'de', 'oceanlabs', 'mcp', 'mcp_config', CFG, `mcp_config-${CFG}.zip`);
if (!existsSync(cfgZip)) throw new Error('没有 ' + cfgZip);
const outDir = join(OUT, MC);
mkdirSync(outDir, { recursive: true });
const tsrgPath = join(outDir, 'joined.tsrg');
{
  const tmp = join(outDir, '.x');
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(tmp, { recursive: true });
  execFileSync(join(JDK, 'jar.exe'), ['xf', cfgZip, 'config/joined.tsrg'], { cwd: tmp, stdio: 'inherit' });
  writeFileSync(tsrgPath, readFileSync(join(tmp, 'config', 'joined.tsrg')));
  rmSync(tmp, { recursive: true, force: true });
}

// ---------- 2) 解析 tsrg v1（`obf srg`，成员行缩进） ----------
//
// 跟 1.16.5 的 tsrg v1 同格式，但右列**已经**是目标命名空间（SRG），不需要二次改名。
const text = readFileSync(tsrgPath, 'utf8');
const classes = [];
const members = []; // [obfClass, 'f'|'m', srcName, dstName, srcDesc]
let cur = null;
let skipped = 0;
for (const raw of text.split(/\r?\n/)) {
  if (!raw.trim()) continue;
  if (raw[0] !== '\t' && raw[0] !== ' ') {
    const [obf, srg] = raw.trim().split(/\s+/);
    if (!obf || !srg) { skipped++; cur = null; continue; }
    cur = obf;
    classes.push([obf, srg]);
    continue;
  }
  if (!cur) { skipped++; continue; }
  const parts = raw.trim().split(/\s+/);
  if (parts.length === 2) members.push([cur, 'f', parts[0], parts[1], null]);
  else if (parts.length === 3) members.push([cur, 'm', parts[0], parts[2], parts[1]]);
  else skipped++;
}
const nf = members.filter((m) => m[1] === 'f').length;
const nm = members.filter((m) => m[1] === 'm').length;
console.log(`  tsrg ${classes.length} classes, ${nf} fields, ${nm} methods (skipped ${skipped})`);

// ---------- 3) 写 tiny v2（official -> srg），成员行描述符在前 ----------
const tinyPath = join(outDir, 'obf2srg.tiny');
const vanillaJar = join(GAME, 'versions', MC, MC + '.jar');
if (!existsSync(vanillaJar)) throw new Error('没有 ' + vanillaJar);
{
  const fieldDescs = readFieldDescs(vanillaJar, outDir);
  const byClass = new Map();
  for (const [obf, srg] of classes) if (!byClass.has(obf)) byClass.set(obf, { dst: srg, mem: [] });
  let noDesc = 0;
  for (const [cls, kind, srcName, dstName, srcDesc] of members) {
    const rec = byClass.get(cls);
    if (!rec) continue;
    if (kind === 'm') {
      rec.mem.push({ kind, desc: srcDesc, srcName, dstName });
    } else {
      const desc = (fieldDescs.get(cls) || new Map()).get(srcName);
      if (!desc) { noDesc++; continue; }
      rec.mem.push({ kind, desc, srcName, dstName });
    }
  }
  if (noDesc) console.log(`  WARN ${noDesc} 个字段在 jar 里找不到描述符（已跳过）`);
  const lines = ['tiny\t2\t0\tofficial\tsrg'];
  for (const [obf, rec] of byClass) {
    lines.push(`c\t${obf}\t${rec.dst}`);
    for (const m of rec.mem) lines.push(`\t${m.kind}\t${m.desc}\t${m.srcName}\t${m.dstName}`);
  }
  writeFileSync(tinyPath, lines.join('\n') + '\n', 'utf8');
}

// ---------- 4) remap 原版 jar ----------
function libCp(mc) {
  const json = JSON.parse(readFileSync(join(GAME, 'versions', mc, mc + '.json'), 'utf8'));
  const out = [];
  for (const lib of json.libraries || []) {
    const [group, artifact, version] = lib.name.split(':');
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.jar';
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out;
}

const srgJar = join(outDir, `client-${MC}-srg.jar`);
console.log(`  remap ${MC}.jar official -> srg`);
execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', vanillaJar, srgJar, tinyPath, 'official', 'srg', ...libCp(MC)], {
  stdio: 'inherit',
  maxBuffer: 1 << 28,
});
console.log('OUT ' + srgJar);
console.log('OUT ' + tinyPath);

// ---------- 5) 顺带把 Forge 的 universal jar 也 remap 成 SRG ----------
//
// **1.12.2 的 Forge universal jar 是 notch 名**（`setRegistryName(nf)`、`Class<? extends avj>`），
// 跟 1.16+ 的 `forge-*-client.jar`（已经 srg）完全不同。所以要自己把它转过来，
// 否则拿它编 mod 时类型全对不上。
const FORGE = arg('forge', null);
if (FORGE) {
  const dir = join(GAME, 'libraries', 'net', 'minecraftforge', 'forge', `${MC}-${FORGE}`);
  // 1.12.2 的文件名没有 `-universal` 后缀（1.13+ 才有），两种都试。
  const uni = [join(dir, `forge-${MC}-${FORGE}-universal.jar`), join(dir, `forge-${MC}-${FORGE}.jar`)].find(existsSync);
  if (!uni) throw new Error('没有 forge universal jar，目录：' + dir);
  const uniOut = join(outDir, `forge-${FORGE}-srg.jar`);
  console.log(`  remap forge-${MC}-${FORGE}-universal.jar official -> srg`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', uni, uniOut, tinyPath, 'official', 'srg', srgJar, ...libCp(MC)], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
  console.log('OUT ' + uniOut);
}
