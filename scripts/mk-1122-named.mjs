// 1.12.2 的 remap 前置：把原版 obf jar 转成 yarn 名（可读 API），供 javac 用。
//
// 本地只有两份 tiny 映射，所以走两步：
//   net.legacyfabric:intermediary:1.12.2     -> official -> intermediary (tiny v1)
//   net.legacyfabric:yarn:1.12.2+build.604   -> intermediary -> named (tiny v2)
// tiny-remapper 一次只吃一份映射。
//
// 产物：modx/legacy/client-1.12.2-named.jar
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, copyFileSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const OUT = join(ROOT, 'modx', 'legacy');
const GAME = join(ROOT, 'testgame');
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';
const JAVA = join(JDK, 'java.exe');

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

// 1.12.2 的 library classpath（remap 时要解析继承关系；natives 跳过）
function libCp() {
  const json = JSON.parse(readFileSync(join(GAME, 'versions', '1.12.2', '1.12.2.json'), 'utf8'));
  const out = [];
  for (const lib of json.libraries) {
    const [group, artifact, version] = lib.name.split(':');
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = group.replace(/\./g, '/') + '/' + artifact + '/' + version + '/' + artifact + '-' + version + '.jar';
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  for (const rel of [
    'net/legacyfabric/intermediary/1.12.2/intermediary-1.12.2.jar',
    'org/lwjgl/lwjgl/lwjgl/2.9.4+legacyfabric.17/lwjgl-2.9.4+legacyfabric.17.jar',
    'org/lwjgl/lwjgl/lwjgl_util/2.9.4+legacyfabric.17/lwjgl_util-2.9.4+legacyfabric.17.jar',
  ]) {
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out;
}

function unzip(jar, entry, destDir) {
  rmSync(destDir, { recursive: true, force: true });
  mkdirSync(destDir, { recursive: true });
  execFileSync(join(JDK, 'jar.exe'), ['xf', jar, entry], { cwd: destDir, stdio: 'inherit' });
  const p = join(destDir, entry);
  if (!existsSync(p)) throw new Error(`${jar} 里没有 ${entry}`);
  return p;
}

function remap(input, output, mappings, from, to, cp) {
  console.log(`  remap ${from} -> ${to}  (${input.split(/[\\/]/).pop()})`);
  execFileSync(JAVA, ['-cp', TR_CP, 'net.fabricmc.tinyremapper.Main', input, output, mappings, from, to, ...cp], {
    stdio: 'inherit',
    maxBuffer: 1 << 28,
  });
}

mkdirSync(OUT, { recursive: true });
const work = join(OUT, 'work');
mkdirSync(work, { recursive: true });

const vanilla = join(GAME, 'versions', '1.12.2', '1.12.2.jar');
const interJar = join(GAME, 'libraries', 'net', 'legacyfabric', 'intermediary', '1.12.2', 'intermediary-1.12.2.jar');
const yarnJar = join(OUT, 'yarn-1.12.2.jar');
for (const p of [vanilla, interJar, yarnJar]) if (!existsSync(p)) throw new Error('缺文件 ' + p);

const tinyInter = unzip(interJar, 'mappings/mappings.tiny', join(work, 'int'));
const tinyYarn = unzip(yarnJar, 'mappings/mappings.tiny', join(work, 'yarn'));

const cp = libCp();
console.log(`library classpath: ${cp.length} 个 jar`);

const mid = join(OUT, 'client-1.12.2-inter.jar');
const named = join(OUT, 'client-1.12.2-named.jar');
remap(vanilla, mid, tinyInter, 'official', 'intermediary', cp);
remap(mid, named, tinyYarn, 'intermediary', 'named', [...cp, mid]);
console.log('\n产出 ' + named);
