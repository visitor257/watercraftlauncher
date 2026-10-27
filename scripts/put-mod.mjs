// 把 dist 里的 jar 装进实例 mods。
//
//   node scripts/put-mod.mjs V1.9.4-FABRIC watercraftlauncher-fabric-1.0.0-mc1.9.4.jar
//
// 两个坑：
//   1. 「版本隔离」标记 .mcver-isolated 是**启动时**才由启动器写入的，装完那一刻可能还没有 ——
//      所以不能靠它猜目录，干脆**两处都放**（versions/<id>/mods 和 testgame/mods）。
//      多放一份无害：Fabric loader 按 mod id 去重，再按 fabric.mod.json 的 depends.minecraft 过滤。
//   2. 共用 mods 目录里不能乱清别人的 jar（会删掉兄弟实例刚装的那份）。
//      只有隔离目录才做清理。
import { copyFileSync, existsSync, mkdirSync, readdirSync, unlinkSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const instance = process.argv[2];
const jarName = process.argv[3];
if (!instance || !jarName) {
  console.error('用法: node scripts/put-mod.mjs <instance> <jarName>');
  process.exit(2);
}

const instDir = join(ROOT, 'testgame', 'versions', instance);
if (!existsSync(instDir)) throw new Error('没有实例目录 ' + instDir);
const src = join(ROOT, 'dist', jarName);
if (!existsSync(src)) throw new Error('没有 ' + src);

const isolated = join(instDir, 'mods');
mkdirSync(isolated, { recursive: true });
for (const f of readdirSync(isolated)) {
  if (f.startsWith('watercraftlauncher-') && f !== jarName) unlinkSync(join(isolated, f));
}
copyFileSync(src, join(isolated, jarName));

const shared = join(ROOT, 'testgame', 'mods');
mkdirSync(shared, { recursive: true });
copyFileSync(src, join(shared, jarName));

console.log(`${instance}: ${jarName} -> versions/${instance}/mods + testgame/mods`);
