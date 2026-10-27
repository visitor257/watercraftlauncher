// 1.12.2 Legacy Fabric 探针构建：
//   javac（对 named jar）  ->  tiny-remapper named->intermediary（loader 要求）  ->  打 jar
// 产物：modx/legacy1122/mcver-probe-1.12.2.jar
import { execFileSync } from 'node:child_process';
import { cpSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const W = join(ROOT, 'modx', 'legacy1122');
const LEG = join(ROOT, 'modx', 'legacy');
const JDK = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin';
const GAME = join(ROOT, 'testgame');

const named = join(LEG, 'client-1.12.2-named.jar');
const inter = join(LEG, 'client-1.12.2-inter.jar');
const yarnTiny = join(LEG, 'work', 'yarn', 'mappings', 'mappings.tiny');

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

for (const p of [named, inter, yarnTiny]) if (!existsSync(p)) throw new Error('缺 ' + p + '（先跑 mk-1122-named.mjs）');

const loaderJar = join(GAME, 'libraries', 'net', 'fabricmc', 'fabric-loader', '0.20.1',
  'fabric-loader-0.20.1.jar');
const loaderCp = existsSync(loaderJar) ? [loaderJar] : [];

const classes = join(W, 'classes');
rmSync(classes, { recursive: true, force: true });
mkdirSync(classes, { recursive: true });

const src = join(W, 'src', 'dev', 'mcver', 'McVerProbe.java');
console.log('javac  (对 named jar)');
execFileSync(join(JDK, 'javac.exe'), [
  '-proc:none', '-nowarn', '-encoding', 'UTF-8', '--release', '8',
  '-cp', [named, ...loaderCp].join(';'),
  '-d', classes, src,
], { stdio: 'inherit' });

writeFileSync(join(classes, 'fabric.mod.json'), JSON.stringify({
  schemaVersion: 1,
  id: 'mcver-probe',
  version: '0.0.1',
  name: 'mcver 1.12.2 probe',
  environment: 'client',
  entrypoints: { client: ['dev.mcver.McVerProbe'] },
  depends: { minecraft: '1.12.2', fabricloader: '>=0.14.0' },
}, null, 2));

const preJar = join(W, 'probe-named.jar');
console.log('jar (named)');
rmSync(preJar, { force: true });
execFileSync(join(JDK, 'jar.exe'), ['cf', preJar, '-C', classes, '.'], { stdio: 'inherit' });

// loader 在运行时按 intermediary 名字做 remap（production 下再 -> obf），所以 jar 必须是 intermediary 名
const outJar = join(W, 'mcver-probe-1.12.2.jar');
console.log('remap named -> intermediary');
rmSync(outJar, { force: true });
execFileSync(join(JDK, 'java.exe'), [
  '-cp', TR_CP, 'net.fabricmc.tinyremapper.Main',
  preJar, outJar, yarnTiny, 'named', 'intermediary', named, inter,
], { stdio: 'inherit' });

console.log('\n产出 ' + outJar);
