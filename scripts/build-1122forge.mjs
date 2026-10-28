// 1.12.2 构建（SRG 命名空间，无 reobf）：
//   javac(SRG jar + Forge universal) -> jar + mcmod.info
//
// 用法： node scripts/build-1122forne.mjs [--spike]
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const GAME = join(ROOT, 'testgame');
const MODX = join(ROOT, 'modx');
const W = join(MODX, 'build', 'spike-1.12.2');
const JAVA_HOME = process.env.JAVA_HOME || 'C:/Program Files/Java/jdk-21.0.7';
const javac = join(JAVA_HOME, 'bin', 'javac.exe');
const jarExe = join(JAVA_HOME, 'bin', 'jar.exe');

const MC = '1.12.2';
const FORGE = '14.23.5.2864';
const srgJar = join(MODX, 'srg', MC, `client-${MC}-srg.jar`);
if (!existsSync(srgJar)) throw new Error('缺 ' + srgJar + '（先跑 node scripts/mk-mcp-srg.mjs）');
// Forge 的 jar 也得是 SRG 版：1.12.2 的 universal jar 是 notch 名，mk-mcp-srg 顺手转过来了
const srgForgeJar = join(MODX, 'srg', MC, `forge-${FORGE}-srg.jar`);
if (!existsSync(srgForgeJar)) throw new Error('缺 ' + srgForgeJar + '（mk-mcp-srg 加 --forge ' + FORGE + '）');

// classpath：原版 + Forge（按 jars 里真实存在的路径拼，natives 跳过）
function libsOf(mc, verId) {
  const json = JSON.parse(readFileSync(join(GAME, 'versions', verId, verId + '.json'), 'utf8'));
  const out = [];
  for (const lib of json.libraries || []) {
    const [group, artifact, version, cls] = lib.name.split(':');
    let rel = lib.downloads && lib.downloads.artifact && lib.downloads.artifact.path;
    if (!rel) rel = `${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}${cls ? '-' + cls : ''}.jar`;
    if (/-natives/.test(rel)) continue;
    const p = join(GAME, 'libraries', rel);
    if (existsSync(p)) out.push(p);
  }
  return out;
}

const cp = [
  srgJar,
  srgForgeJar,
  ...libsOf(MC, MC),
  ...libsOf(MC, `${MC}-forge-${FORGE}`),
].join(';');

rmSync(W, { recursive: true, force: true });
mkdirSync(join(W, 'classes'), { recursive: true });
mkdirSync(join(W, 'src'), { recursive: true });
cpSync(join(MODX, 'spike1122', 'java'), join(W, 'src'), { recursive: true });

const sources = [];
(function walk(d) {
  for (const e of readdirSync(d, { withFileTypes: true })) {
    const p = join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (p.endsWith('.java')) sources.push(p);
  }
})(join(W, 'src'));

console.log(`  javac ${sources.length} files (--release 8)`);
execFileSync(javac, [
  '-J-Duser.language=en', '-J-Duser.country=US',
  '--release', '8',
  '-nowarn',
  '-encoding', 'UTF-8',
  '-cp', cp,
  '-d', join(W, 'classes'),
  ...sources,
], { stdio: 'inherit', maxBuffer: 1 << 28 });

cpSync(join(MODX, 'spike1122', 'resources'), join(W, 'classes'), { recursive: true });

const outJar = join(MODX, 'out', 'spike1122', 'spike-1.12.2.jar');
mkdirSync(join(MODX, 'out', 'spike1122'), { recursive: true });
rmSync(outJar, { force: true });
execFileSync(jarExe, ['cf', outJar, '-C', join(W, 'classes'), '.'], { stdio: 'inherit' });
console.log('OK ' + outJar);
