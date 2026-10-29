// 1.7.x 逐版本构建 + 装进实例 + 像素验收
//
//   node scripts/build-17x.mjs              # 默认跑 1.7 1.7.1 1.7.2 ...
//   node scripts/build-17x.mjs 1.7.6 1.7.7
//
// 为什么逐版本出 jar（而不是一个区间盖 1.7 全系）：legacy Fabric 的 jar 都是精确版本
// （1.9.4 / 1.10.2 / 1.11.2 / 1.12.2 都是），因为「支持」必须逐个验证过才算 ——
// 1.7.x 的中间名 id 虽然实测一致，但装配到 jar 里的 depends 一旦写区间，
// 就等于对没跑过的版本打包票。
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const MC10 = ['1.7', '1.7.1', '1.7.2', '1.7.3', '1.7.4', '1.7.5', '1.7.6', '1.7.7', '1.7.8'];

const wanted = process.argv.slice(2).filter((a) => !a.startsWith('--'));
const list = wanted.length ? wanted : MC10;

function instanceOf(mc) {
  // 早期手工建的实例叫 V<mc>-FABRIC，后面脚本建的叫 fabric-loader-0.20.1-<mc>
  for (const id of [`V${mc}-FABRIC`, `fabric-loader-0.20.1-${mc}`]) {
    if (existsSync(join(ROOT, 'testgame', 'versions', id, `${id}.json`))) return id;
  }
  return null;
}

const rows = [];
for (const mc of list) {
  const inst = instanceOf(mc);
  if (!inst) { console.log(`\n==== ${mc}: 没有实例，跳过（先跑 support-17x.mjs）`); rows.push([mc, '-', 'NO-INSTANCE', '-']); continue; }
  console.log(`\n==== ${mc}  (${inst})`);

  try {
    const out = execFileSync('node', ['scripts/build-1132.mjs', mc, '--instance', inst], { cwd: ROOT, encoding: 'utf8' });
    const line = out.trim().split('\n').filter((l) => l.includes('产出')).pop() || '';
    console.log('  ' + line.trim());
  } catch (e) {
    console.log('  构建失败: ' + String(e.stdout || e.message).split('\n').filter((l) => /错误|符号/.test(l)).slice(0, 6).join(' | '));
    rows.push([mc, inst, 'BUILD-FAIL', '-']);
    continue;
  }

  const jar = `watercraftlauncher-fabric-1.0.0-mc${mc}.jar`;
  execFileSync('node', ['scripts/put-mod.mjs', inst, jar], { cwd: ROOT, encoding: 'utf8' });

  let ok = false;
  let detail = '';
  try {
    const out = execFileSync('node', ['scripts/verify-gui-one.mjs', inst], { cwd: ROOT, encoding: 'utf8' });
    const j = JSON.parse(out.slice(out.indexOf('{')));
    ok = j.ok === true;
    detail = `center=${j.center} hits=${j.hits} children=${j.children}`;
  } catch (e) {
    detail = String(e.stdout || e.message).split('\n').slice(-3).join(' ');
  }
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${detail}`);
  rows.push([mc, inst, ok ? 'PASS' : 'FAIL', detail]);
}

console.log('\n==== 汇总');
for (const r of rows) console.log(r.join('\t'));
