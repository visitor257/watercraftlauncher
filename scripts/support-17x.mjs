// 铺 1.7.x：装实例 → 出 named jar → 报 Screen/DrawableHelper 的映射风格
//
//   node scripts/support-17x.mjs 1.7 1.7.1 1.7.3 ...
//
// 判据（两代两种写法，二选一）：
//   完整命名（Screen 上有 render/tick/buttons）→ 用 compat/1.7.10
//   只有 method_xxx（yarn 没映射）        → 用 compat/1.7.2，前提是 id 和 1.7.2 一致
// 这一步只负责「装 + 拿 jar + 体检」，构建和像素验收另跑。
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const ROOT = resolve(import.meta.dirname, '..');
const JAVA = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin\\java.exe';
const JAVAP = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin\\javap.exe';

const versions = process.argv.slice(2).filter((a) => !a.startsWith('--'));
if (!versions.length) {
  console.error('用法: node scripts/support-17x.mjs <mc> [mc...]');
  process.exit(2);
}

function javap(jar, cls) {
  try {
    return execFileSync(JAVAP, ['-p', '-cp', jar, cls], { encoding: 'utf8' });
  } catch {
    return '';
  }
}

const rows = [];
for (const mc of versions) {
  const id = `fabric-loader-0.20.1-${mc}`;
  const json = join(ROOT, 'testgame', 'versions', id, `${id}.json`);

  if (!existsSync(json)) {
    console.log(`\n==== ${mc}: 装实例`);
    try {
      const out = execFileSync(JAVA, ['-cp', join(ROOT, 'tools', 'probe', 'out'), 'dev.mcver.probe.ProbeInstall',
        'testgame', mc, 'FABRIC', '-', 'NONE', '-', '-', '1'], { encoding: 'utf8', cwd: ROOT });
      const last = out.trim().split('\n').slice(-2).join(' ');
      console.log(`  ${last}`);
      if (!out.includes('[probe] OK')) { rows.push([mc, 'INSTALL-FAIL', '-', '-']); continue; }
    } catch (e) {
      console.log(`  装失败: ${String(e.stdout || e.message).split('\n').slice(-3).join(' ')}`);
      rows.push([mc, 'INSTALL-FAIL', '-', '-']);
      continue;
    }
  } else {
    console.log(`\n==== ${mc}: 实例已存在`);
  }

  const named = join(ROOT, 'modx', 'legacy', mc, `client-${mc}-named.jar`);
  if (!existsSync(named)) {
    console.log(`  出 named jar ...`);
    try {
      execFileSync('node', ['scripts/mk-legacy-named.mjs', mc, '--instance', id], { cwd: ROOT, stdio: 'inherit' });
    } catch {
      console.log('  named jar 失败');
      rows.push([mc, 'NAMED-FAIL', '-', '-']);
      continue;
    }
  }

  const screen = javap(named, 'net.minecraft.client.gui.screen.Screen');
  const style = /void render\(int, int, float\)/.test(screen) ? 'yarn-1.7.10'
    : /method_21925\(int, int, float\)/.test(screen) ? 'method_219xx' : 'unknown';
  const dh = javap(named, 'net.minecraft.client.gui.DrawableHelper');
  const fill = /fill\(int, int, int, int, int\)/.test(dh) ? 'fill'
    : /method_21878\(int, int, int, int, int\)/.test(dh) ? 'method_21878' : 'unknown';
  const items = javap(named, 'net.minecraft.client.gui.widget.ButtonWidget');
  const msg = /field_22510/.test(items) ? 'field_22510' : /String message/.test(items) ? 'message' : 'unknown';
  console.log(`  Screen=${style}  fill=${fill}  button.message=${msg}`);
  rows.push([mc, style, fill, msg]);
}

console.log('\n==== 汇总');
for (const r of rows) console.log(r.join('\t'));
