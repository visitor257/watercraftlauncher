// 1.8 ~ 1.12 那一代的 remap 前置：原版 obf jar -> **SRG** jar。
//
//   mc 1.7.10 / 1.8.9 / 1.9.4 / 1.10.2 / 1.11.2 / 1.12.2 的映射都在
//   `de/oceanlabs/mcp/mcp/<mc>/mcp-<mc>-srg.zip` 里的 **joined.srg（v0 文本格式）**：
//
//     PK: <obf 包> <srg 包>
//     CL: <obf 类> <srg 类全路径>
//     FD: <obf 类>/<name> <srg 类全路径>/<name>
//     MD: <obf 类>/<name> (<obf 描述符>) <srg 类>/<name> (<srg 描述符>)
//
//   跟 tsrg 的两处差别：① 成员行把 owner 写在名字里（`bao/a`），不是缩进；
//   ② 字段行**没有描述符**（tsrg 也没有，所以照样得去 jar 里读）。
//
// 另外：这一代的 **Forge universal jar 也是 notch 名**（FML 的 deobf transformer 在运行期
// 把它翻成 srg），所以也要一起 remap，否则拿它编 mod 时类型全对不上。
//
// 产出：modx/srg/<mc>/client-<mc>-srg.jar、forge-<ver>-srg.jar、obf2srg.tiny
//
// 用法： node scripts/mk-srg-srgv0.mjs --mc 1.10.2 --instance <实例名> --forge <ver>
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
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
const MC = arg('mc', '1.7.10');
const INSTANCE = arg('instance', `${MC}-Forge${arg('forge', '10.13.4.1614')}-${MC}`);
const VANILLA = arg('jar', join(GAME, 'versions', INSTANCE, INSTANCE + '.jar'));
const FORGE_VER = arg('forge', '10.13.4.1614');
// 目录名和文件名各代不一样：1.7.10 / 1.8~1.12 有的带 `-<mc>` 尾巴、有的不带。全试一遍。
const FORGE_DIR = [
  join(GAME, 'libraries', 'net', 'minecraftforge', 'forge', `${MC}-${FORGE_VER}-${MC}`),
  join(GAME, 'libraries', 'net', 'minecraftforge', 'forge', `${MC}-${FORGE_VER}`),
].find(existsSync) || join(GAME, 'libraries', 'net', 'minecraftforge', 'forge', `${MC}-${FORGE_VER}`);
const FORGE_JAR = arg('forge-jar', [
  join(FORGE_DIR, `forge-${MC}-${FORGE_VER}-${MC}.jar`),
  join(FORGE_DIR, `forge-${MC}-${FORGE_VER}-universal.jar`),
  join(FORGE_DIR, `forge-${MC}-${FORGE_VER}.jar`),
].find(existsSync) || join(FORGE_DIR, `forge-${MC}-${FORGE_VER}.jar`));
const SRG_ZIP = arg('zip', join(OUT, MC, `mcp-${MC}-srg.zip`));

// tiny-remapper 运行时（复用 Gradle 缓存里已有的 jar）
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
function parseClassFields(buf) {
  const dv = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  let p = 0;
  const u1 = () => buf[p++];
  const u2 = () => { const v = dv.getUint16(p); p += 2; return v; };
  const u4 = () => { const v = dv.getUint32(p); p += 4; return v; };
  if (u4() !== 0xcafebabe) throw new Error('bad magic');
  u2(); u2();
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
  u2();
  const thisClass = u2();
  u2();
  const ifCount = u2();
  p += ifCount * 2;
  const fields = [];
  const fieldCount = u2();
  for (let i = 0; i < fieldCount; i++) {
    u2();
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

// ---------- 1) 取 joined.srg ----------
if (!existsSync(SRG_ZIP)) throw new Error('没有 ' + SRG_ZIP + `（先下 mcp-${MC}-srg.zip）`);
const outDir = join(OUT, MC);
mkdirSync(outDir, { recursive: true });
const srgPath = join(outDir, 'joined.srg');
{
  const tmp = join(outDir, '.x');
  rmSync(tmp, { recursive: true, force: true });
  mkdirSync(tmp, { recursive: true });
  execFileSync(join(JDK, 'jar.exe'), ['xf', SRG_ZIP, 'joined.srg'], { cwd: tmp, stdio: 'inherit' });
  writeFileSync(srgPath, readFileSync(join(tmp, 'joined.srg')));
  rmSync(tmp, { recursive: true, force: true });
}

// ---------- 2) 解析 joined.srg ----------
const text = readFileSync(srgPath, 'utf8');
const classes = [];
const members = []; // [obfClass, 'f'|'m', srcName, dstName, srcDesc]
let skipped = 0;
for (const raw of text.split(/\r?\n/)) {
  const line = raw.trim();
  if (!line || line.startsWith('#')) continue;
  const kind = line.slice(0, 2);
  if (kind === 'PK') continue;
  if (kind === 'CL') {
    const m = /^CL:\s+(\S+)\s+(\S+)$/.exec(line);
    if (!m) { skipped++; continue; }
    classes.push([m[1], m[2]]);
    continue;
  }
  if (kind === 'FD') {
    const m = /^FD:\s+(\S+)\/([^/\s]+)\s+(\S+)\/([^/\s]+)$/.exec(line);
    if (!m) { skipped++; continue; }
    members.push([m[1], 'f', m[2], m[4], null]);
    continue;
  }
  if (kind === 'MD') {
    const m = /^MD:\s+(\S+)\/([^/\s(]+)\s+(\([^)]*\)\S*)\s+(\S+)\/([^/\s(]+)\s+(\([^)]*\)\S*)$/.exec(line);
    if (!m) { skipped++; continue; }
    members.push([m[1], 'm', m[2], m[5], m[3]]);
    continue;
  }
  skipped++;
}
const nf = members.filter((m) => m[1] === 'f').length;
const nm = members.filter((m) => m[1] === 'm').length;
console.log(`  joined.srg ${classes.length} classes, ${nf} fields, ${nm} methods (skipped ${skipped})`);
if (!classes.length) throw new Error('解析出 0 个类 —— 格式不对');

// ---------- 3) 写 tiny v2（official -> srg），成员行描述符在前 ----------
const tinyPath = join(outDir, 'obf2srg.tiny');
if (!existsSync(VANILLA)) throw new Error('没有 ' + VANILLA);
{
  const fieldDescs = readFieldDescs(VANILLA, outDir);
  const byClass = new Map();
  for (const [obf, srg] of classes) if (!byClass.has(obf)) byClass.set(obf, { dst: srg, mem: [] });
  let noDesc = 0;
  let noClass = 0;
  for (const [cls, kind, srcName, dstName, srcDesc] of members) {
    const rec = byClass.get(cls);
    if (!rec) { noClass++; continue; }
    if (kind === 'm') {
      rec.mem.push({ kind, desc: srcDesc, srcName, dstName });
    } else {
      const desc = (fieldDescs.get(cls) || new Map()).get(srcName);
      if (!desc) { noDesc++; continue; }
      rec.mem.push({ kind, desc, srcName, dstName });
    }
  }
  if (noDesc) console.log(`  WARN ${noDesc} 个字段在 jar 里找不到描述符（已跳过）`);
  if (noClass) console.log(`  WARN ${noClass} 个成员的 owner 不在 CL 表里（已跳过）`);
  const lines = ['tiny\t2\t0\tofficial\tsrg'];
  for (const [obf, rec] of byClass) {
    lines.push(`c\t${obf}\t${rec.dst}`);
    for (const m of rec.mem) lines.push(`\t${m.kind}\t${m.desc}\t${m.srcName}\t${m.dstName}`);
  }
  writeFileSync(tinyPath, lines.join('\n') + '\n', 'utf8');
  console.log(`  tiny v2: ${lines.length - 1} 行`);
}

// ---------- 4) remap 原版 jar ----------
function libCp(instance) {
  const json = JSON.parse(readFileSync(join(GAME, 'versions', instance, instance + '.json'), 'utf8'));
  const out = [];
  for (const lib of json.libraries || []) {
    const parts = lib.name.split(':');
    const [group, artifact, version] = parts;
    const classifier = parts[3];
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) {
      rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version
        + (classifier ? '-' + classifier : '') + '.jar';
    }
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out;
}

const srgJar = join(outDir, `client-${MC}-srg.jar`);
console.log(`  remap ${INSTANCE}.jar official -> srg`);
execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', VANILLA, srgJar, tinyPath, 'official', 'srg', ...libCp(INSTANCE)], {
  stdio: 'inherit',
  maxBuffer: 1 << 28,
});
console.log('OUT ' + srgJar);

// ---------- 5) Forge universal jar：也是 notch 名，一样要 remap ----------
if (existsSync(FORGE_JAR)) {
  const forgeOut = join(outDir, `forge-${FORGE_VER}-srg.jar`);
  console.log(`  remap ${FORGE_JAR.split(/[\\/]/).pop()} official -> srg`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', FORGE_JAR, forgeOut, tinyPath, 'official', 'srg', srgJar, ...libCp(INSTANCE)], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
  console.log('OUT ' + forgeOut);
} else {
  console.log(`  WARN 没有 forge jar: ${FORGE_JAR}`);
}
