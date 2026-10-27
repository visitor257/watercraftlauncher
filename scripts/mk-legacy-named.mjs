// Legacy Fabric 通用前置：把原版 obf jar 转成 yarn named jar（供 javac 用）。
//
//   node scripts/mk-legacy-named.mjs 1.13.2
//   node scripts/mk-legacy-named.mjs 1.13.2 --instance V1.13.2-FABRIC   # 实例已合并成单版本时
//
// 两步 remap（tiny-remapper 一次只吃一份映射）：
//   net.legacyfabric:intermediary:<mc>  -> official -> intermediary（tiny v1）
//   net.legacyfabric:yarn:<mc>+build.N  -> intermediary -> named（tiny v2）
//
// 产物：modx/legacy/<mc>/client-<mc>-named.jar
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const argv = process.argv.slice(2);
const mc = argv[0];
if (!mc || mc.startsWith('--')) {
  console.error('用法: node scripts/mk-legacy-named.mjs <mc> [--instance <versionId>] [--yarn <ver>]');
  process.exit(2);
}
const argOf = (k) => {
  const i = argv.indexOf(k);
  return i >= 0 ? argv[i + 1] : null;
};
const instance = argOf('--instance');

const ROOT = resolve(import.meta.dirname, '..');
const OUT = join(ROOT, 'modx', 'legacy', mc);
const GAME = join(ROOT, 'testgame');
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';
const JAVA = join(JDK, 'java.exe');

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

// ---- 找原版 jar：实例可能已经「合并成单一版本」，此时 jar 叫 <versionId>.jar ----
function findVersion() {
  const dirs = [];
  const base = join(GAME, 'versions');
  for (const d of readdirSync(base)) {
    const p = join(base, d);
    if (!statSync(p).isDirectory()) continue;
    const json = join(p, `${d}.json`);
    if (!existsSync(json)) continue;
    dirs.push({ id: d, dir: p, json });
  }
  const order = instance
    ? [instance, mc]
    : [mc, ...dirs.map((d) => d.id).filter((id) => id !== mc)];
  for (const id of order) {
    const hit = dirs.find((d) => d.id === id);
    if (!hit) continue;
    for (const f of readdirSync(hit.dir)) {
      if (!f.endsWith('.jar')) continue;
      // 同意：<mc>.jar 或 <versionId>.jar（合并版），排除 -sources/natives
      if (/natives|sources|javadoc/i.test(f)) continue;
      if (f === `${mc}.jar` || f === `${hit.id}.jar`) return { ...hit, jar: join(hit.dir, f) };
    }
  }
  throw new Error(`找不到 ${mc} 的原版 jar（versions/ 下没有 ${mc}.jar 或 ${instance || '?'}.jar）`);
}

// ---- classpath：优先用 json 里的 downloads.artifact.path，loader 库按 name 推路径 ----
function libCp(jsonPath) {
  const json = JSON.parse(readFileSync(jsonPath, 'utf8'));
  const seen = new Set();
  const out = [];
  for (const lib of json.libraries || []) {
    const parts = lib.name.split(':');
    const [group, artifact, version, classifier] = parts;
    if (classifier && /native/i.test(classifier)) continue;
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) {
      rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.jar';
    }
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (!existsSync(p)) { console.log(`  [warn] 缺库 ${rel}`); continue; }
    if (seen.has(p)) continue;
    seen.add(p);
    out.push(p);
  }
  return out;
}

mkdirSync(OUT, { recursive: true });

const ver = findVersion();
console.log(`原版 jar: ${ver.jar}`);
console.log(`版本 json: ${ver.json}`);

const interJar = join(OUT, `intermediary-${mc}.jar`);
const yarnJar = join(OUT, `yarn-${mc}.jar`);

async function download(url, dest, minBytes = 10000) {
  if (existsSync(dest) && statSync(dest).size > minBytes) return false;
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url} -> HTTP ${r.status}`);
  writeFileSync(dest, Buffer.from(await r.arrayBuffer()));
  console.log(`  下载 ${dest.split(/[\\/]/).pop()} (${Math.round(statSync(dest).size / 1024)}KB)`);
  return true;
}

// Legacy Fabric 的 maven 路径多一层 /legacyfabric/，且 + 要 encode
const MAVEN = 'https://repo.legacyfabric.net/legacyfabric';
await download(`${MAVEN}/net/legacyfabric/intermediary/${mc}/intermediary-${mc}.jar`, interJar);

let yarnVer = argOf('--yarn');
if (!yarnVer) {
  const meta = await (await fetch(`https://meta.legacyfabric.net/v2/versions/yarn/${mc}`)).json();
  if (!Array.isArray(meta) || !meta.length) throw new Error(`meta 上没有 ${mc} 的 yarn`);
  const stable = meta.filter((v) => v.stable);
  yarnVer = (stable.length ? stable : meta)[0].version;
  console.log(`  yarn 版本取 ${yarnVer}`);
}
await download(`${MAVEN}/net/legacyfabric/yarn/${encodeURIComponent(yarnVer)}/yarn-${encodeURIComponent(yarnVer)}-v2.jar`, yarnJar);

function unzipEntry(jar, entry, destDir) {
  mkdirSync(destDir, { recursive: true });
  execFileSync(join(JDK, 'jar.exe'), ['xf', jar, entry], { cwd: destDir, stdio: 'inherit' });
  const p = join(destDir, entry);
  if (!existsSync(p)) throw new Error(`${jar} 里没有 ${entry}`);
  return p;
}
const tinyInter = unzipEntry(interJar, 'mappings/mappings.tiny', join(OUT, 'work', 'int'));
const tinyYarn = unzipEntry(yarnJar, 'mappings/mappings.tiny', join(OUT, 'work', 'yarn'));

const cp = libCp(ver.json);
console.log(`library classpath: ${cp.length} 个 jar`);

function remap(input, output, mappings, from, to, extra) {
  console.log(`  remap ${from} -> ${to}`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', input, output, mappings, from, to, ...extra], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
}

const mid = join(OUT, `client-${mc}-inter.jar`);
const named = join(OUT, `client-${mc}-named.jar`);
remap(ver.jar, mid, tinyInter, 'official', 'intermediary', cp);
remap(mid, named, tinyYarn, 'intermediary', 'named', [...cp, mid]);
console.log('\n产出 ' + named);
