#!/usr/bin/env node
/**
 * 把一个存档克隆到别的实例，顺手改版本号（降级/升级）和游戏模式。
 *
 * 为什么要它：`ProbeRightClick` 需要「目标版本能直接进的存档」+「创造模式 + 允许作弊」
 * （否则 `/give` 没权限）。而造存档最快的路子有两个：
 *
 * 1. `mk-world.mjs` —— 起对应版本的服务端生成（干净，但要下载 server.jar，国内很慢）；
 * 2. 本脚本 —— 直接拿现成存档改头（秒级，代价是版本跨度大时方块/实体可能缺，仅够测试用）。
 *
 * 实测：把 1.21.4 的存档 DataVersion 改成 3463 塞给 1.20.0，能正常加载、能进世界
 * （会刷一堆 `Skipping BlockEntity with id minecraft:trial_spawner`，无所谓）。
 *
 * 用法：
 *   node scripts/clone-world.mjs --from V1.21.4-FABRIC/新的世界 --to fabric-loader-0.20.1-1.20 --name flat120 --dv 3463 --ver 1.20
 *
 * 选项：
 *   --from <实例>/<存档名>   源
 *   --to   <实例>            目标实例（可以逗号分隔多个）
 *   --name <存档名>          目标存档名（默认沿用源的名字）
 *   --dv   <DataVersion>     不给就不改（1.20=3463 / 1.20.1=3465 / 1.21.4=4189）
 *   --ver  <版本名>          配合 --dv 用，写进 Data.Version.Name
 *   --keep-gametype          别改成创造模式
 */
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

const from = arg('from', '');
const toList = (arg('to', '') || '').split(',').map((s) => s.trim()).filter(Boolean);
const dv = arg('dv', '');
const ver = arg('ver', '');
if (!from.includes('/') || !toList.length) {
  console.error('用法: node scripts/clone-world.mjs --from <实例>/<存档名> --to <实例>[,<实例>] [--name 名字] [--dv 3463 --ver 1.20]');
  process.exit(2);
}

const [srcInst, srcWorld] = from.split('/');
const src = path.join(gameDir, 'versions', srcInst, 'saves', srcWorld);
if (!fs.existsSync(path.join(src, 'level.dat'))) {
  console.error(`源存档不存在: ${src}`);
  process.exit(2);
}

for (const inst of toList) {
  const name = arg('name', srcWorld);
  const dst = path.join(gameDir, 'versions', inst, 'saves', name);
  fs.mkdirSync(path.dirname(dst), { recursive: true });
  if (fs.existsSync(dst)) fs.rmSync(dst, { recursive: true, force: true });
  fs.cpSync(src, dst, { recursive: true });
  const lock = path.join(dst, 'session.lock');
  if (fs.existsSync(lock)) fs.rmSync(lock);

  const tree = readLevelDat(path.join(dst, 'level.dat'));
  const data = dataOf(tree);
  const before = {
    GameType: data.get('GameType')?.v,
    allowCommands: data.get('allowCommands')?.v,
    DataVersion: data.get('DataVersion')?.v,
  };

  if (dv) {
    const n = Number(dv);
    data.set('DataVersion', I(n));
    const v = data.get('Version');
    if (v?.v?.get) {
      v.v.set('Id', I(n));
      if (ver) v.v.set('Name', STR(ver));
      v.v.set('Snapshot', B(0));
    }
  }
  if (!has('keep-gametype')) {
    data.set('GameType', I(1));         // 创造
    data.set('allowCommands', B(1));    // 允许作弊（/give 要用）
    if (data.has('Player')) data.delete('Player'); // 旧的单人数据带着旧模式
  }

  writeLevelDat(path.join(dst, 'level.dat'), tree);
  const after = dataOf(readLevelDat(path.join(dst, 'level.dat')));
  console.log(
    `[clone] ${inst}/saves/${name}: GameType ${before.GameType}->${after.get('GameType')?.v}` +
    `, allowCommands ${before.allowCommands}->${after.get('allowCommands')?.v}` +
    `, DataVersion ${before.DataVersion}->${after.get('DataVersion')?.v}`
  );
}
