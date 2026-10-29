#!/usr/bin/env node
/**
 * 造一个「探针能直接用」的存档：起一次 1.20.1 服务端生超平坦世界 → 拷进实例 saves/
 * → 改 level.dat（创造模式 + 允许作弊）。
 *
 * 为什么需要它：`ProbeRightClick` 要靠 `--quickPlaySingleplayer <存档>` 直接进世界，
 * 再用 `/give` 拿物品、真右键。自己造存档要 GUI 点半天；服务端生成是纯命令行、可复现。
 *
 * 注意两点：
 * 1. 服务端生成出来的 level.dat 里 `allowCommands=0`、`GameType=0`（生存），
 *    进单机后 `/give` 会被拒。所以必须改 level.dat（见 patchLevelDat）。
 * 2. 世界版本要和目标实例一致。1.21.4 世界的 DataVersion 比 1.20.1 高，单机直接拒绝加载。
 *
 * 用法：
 *   node scripts/mk-world.mjs --mc 1.20.1 --name flat120 --save-into fabric-loader-0.20.1-1.20
 *   node scripts/mk-world.mjs --mc 1.20.1 --name flat120 --save-into a,b,c   # 多个实例
 *
 * 选项：
 *   --server-jar <路径>   已经下好的 server.jar（不给就按 --mc 从 Mojang 拉）
 *   --java <路径>         用哪个 java 跑服务端（默认 jdk-21）
 *   --keep                服务端目录留着（默认留着，方便复用）
 */
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { B, I, STR, readLevelDat, writeLevelDat, dataOf } from './nbt.mjs';

const root = path.resolve(import.meta.dirname, '..');
const gameDir = path.join(root, 'testgame');

function arg(name, dflt) {
  const i = process.argv.indexOf('--' + name);
  return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : dflt;
}
const has = (name) => process.argv.includes('--' + name);

const mc = arg('mc', '1.20.1');
const name = arg('name', 'flat' + mc.replace(/\./g, ''));
const saveInto = (arg('save-into', '') || '').split(',').map((s) => s.trim()).filter(Boolean);
const java = arg('java', 'D:\\Program Files\\Java\\jdk-21.0.7\\bin\\java.exe');
const dv = arg('dv', '');      // 目标 DataVersion（把服务端世界压到更旧的客户端能认的版本）
const dvVer = arg('ver', '');  // 配合 --dv 的版本名
const work = path.join(gameDir, 'rcserver', 'gen-' + mc);
const serverJar = arg('server-jar', path.join(work, 'server.jar'));

if (!saveInto.length) {
  console.error('用法: node scripts/mk-world.mjs --mc 1.20.1 --name flat120 --save-into <实例>[,<实例>...]');
  process.exit(2);
}

// ---------------------------------------------------------------- 1. server.jar
async function fetchServerJar() {
  if (fs.existsSync(serverJar) && fs.statSync(serverJar).size > 1e6) return;
  console.log(`[world] 下载 ${mc} server.jar ...`);
  const meta = JSON.parse(await text(`https://launchermeta.mojang.com/mc/game/version_manifest_v2.json`));
  const v = meta.versions.find((x) => x.id === mc);
  if (!v) throw new Error(`版本清单里没有 ${mc}`);
  const ver = JSON.parse(await text(v.url));
  const url = ver.downloads?.server?.url;
  if (!url) throw new Error(`${mc} 没有 server 下载（远古版/愚人节版没有服务端）`);
  fs.mkdirSync(path.dirname(serverJar), { recursive: true });
  const buf = await bufFetch(url);
  fs.writeFileSync(serverJar, buf);
  console.log(`[world] server.jar ${buf.length} 字节`);
}

async function text(url) {
  return (await bufFetch(url)).toString('utf8');
}
async function bufFetch(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${url}`);
  return Buffer.from(await res.arrayBuffer());
}

// ---------------------------------------------------------------- 2. 跑一次服务端
function serverProperties() {
  return [
    'level-name=world',
    'level-type=flat',
    'level-seed=1',
    'online-mode=false',
    'spawn-protection=0',
    'max-players=2',
    'view-distance=4',
    'simulation-distance=4',
    'motd=mcver-worldgen',
    'enable-command-block=false',
  ].join('\n') + '\n';
}

function runServer() {
  const worldDir = path.join(work, 'world');
  if (fs.existsSync(path.join(worldDir, 'level.dat')) && !has('force')) {
    console.log('[world] 已经有生成好的世界，跳过服务端');
    return;
  }
  fs.mkdirSync(work, { recursive: true });
  fs.writeFileSync(path.join(work, 'eula.txt'), 'eula=true\n');
  fs.writeFileSync(path.join(work, 'server.properties'), serverProperties());

  console.log('[world] 起服务端生成世界（约 1-2 分钟）...');
  return new Promise((resolve, reject) => {
    const child = spawn(java, ['-Xmx1024M', '-jar', serverJar, 'nogui'], { cwd: work });
    let out = '';
    const killTimer = setTimeout(() => {
      console.log('[world] 服务端超时，强制结束');
      child.kill();
    }, 300_000);
    child.stdout.on('data', (d) => {
      const s = d.toString('utf8');
      out += s;
      for (const line of s.split(/\r?\n/)) {
        if (!line.trim()) continue;
        if (/Done \(|Preparing spawn area|Starting minecraft server/.test(line)) console.log('  ' + line.trim());
      }
      if (/Done \(/.test(out)) {
        // 世界已经落盘了，但还差一次完整保存 —— 让它自己 stop
        child.stdin.write('stop\n');
      }
    });
    child.stderr.on('data', (d) => { out += d.toString('utf8'); });
    child.on('exit', (code) => {
      clearTimeout(killTimer);
      if (!fs.existsSync(path.join(worldDir, 'level.dat'))) {
        reject(new Error(`服务端没生成出世界（exit ${code}）\n${out.slice(-2000)}`));
        return;
      }
      console.log(`[world] 生成完成（exit ${code}）`);
      resolve();
    });
  });
}

// ---------------------------------------------------------------- 3. 改 level.dat

/** 创造模式 + 允许作弊：进单机后 `/give` 才有权限。 */
function patchLevelDat(file) {
  const tree = readLevelDat(file);
  const data = dataOf(tree);
  const before = {
    GameType: data.get('GameType')?.v,
    allowCommands: data.get('allowCommands')?.v,
  };
  data.set('GameType', I(1));
  data.set('allowCommands', B(1));
  if (dv) {
    const n = Number(dv);
    data.set('DataVersion', I(n));
    const v = data.get('Version');
    if (v?.v?.get) {
      v.v.set('Id', I(n));
      if (dvVer) v.v.set('Name', STR(dvVer));
    }
  }
  if (data.has('Player')) data.delete('Player'); // 旧的单人数据带着旧模式，删掉更干净
  writeLevelDat(file, tree);
  console.log(`[world] level.dat: GameType ${before.GameType} -> 1, allowCommands ${before.allowCommands} -> 1`);
}

// ---------------------------------------------------------------- main
await fetchServerJar();
await runServer();

const src = path.join(work, 'world');
for (const inst of saveInto) {
  const dst = path.join(gameDir, 'versions', inst, 'saves', name);
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  if (fs.existsSync(dst)) {
    fs.rmSync(dst, { recursive: true, force: true });
    console.log(`[world] 覆盖 ${inst}/saves/${name}`);
  }
  fs.cpSync(src, dst, { recursive: true });
  for (const f of ['session.lock']) {
    const p = path.join(dst, f);
    if (fs.existsSync(p)) fs.rmSync(p);
  }
  patchLevelDat(path.join(dst, 'level.dat'));
  console.log(`[world] ${inst} <- ${name}`);
}
console.log('[world] 用法: node scripts/verify-rightclick.mjs <实例> --world ' + name);
