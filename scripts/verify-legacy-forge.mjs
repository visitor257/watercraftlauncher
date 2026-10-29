import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

// 老 Forge 家族验收：模型登记 / 配方登记 / 右键钩子 是否真的生效。
// 判据只认日志里的 ASCII 标记（中文在 GBK 日志里是乱码，探针按 UTF-8 找不到）。
const JAVA = 'D:/Program Files/Java/jdk-21.0.7/bin/java.exe';
const cases = [
  ['1.12.2-forge-14.23.5.2864', '1.12.2', 'mcver: recipe loaded download_tool=true'],
  ['1.11.2-forge1.11.2-13.20.1.2588', '1.11.2', 'mcver: recipe download_tool=true'],
  ['1.10.2-forge1.10.2-12.18.3.2511', '1.10.2', 'mcver: recipe download_tool=true'],
  ['1.9.4-forge1.9.4-12.17.0.2317-1.9.4', '1.9.4', 'mcver: recipe download_tool=true'],
  ['1.8.9-forge1.8.9-11.15.1.2318-1.8.9', '1.8.9', 'mcver: recipe download_tool=true'],
  ['1.7.10-Forge10.13.4.1614-1.7.10', '1.7.10', 'mcver: recipe download_tool=true'],
  ['1.7.2-Forge10.12.2.1161-mc172', '1.7.2', 'mcver: recipe download_tool=true'],
];

const only = process.argv[2];
let pass = 0, fail = 0, skip = 0;
for (const [inst, mc, marker] of cases) {
  if (only && mc !== only) continue;
  if (!existsSync(join('testgame', 'versions', inst))) { console.log(`SKIP ${mc} 没实例 ${inst}`); skip++; continue; }
  execFileSync(process.execPath, ['scripts/put-mod.mjs', inst, `watercraftlauncher-forge-1.0.0-mc${mc}.jar`], { stdio: 'ignore' });
  let out = '';
  try {
    out = execFileSync(JAVA, ['-cp', 'tools/probe/out', 'dev.mcver.probe.ProbeLaunch', 'testgame', inst, '180', marker],
      { encoding: 'utf8', maxBuffer: 1 << 26 });
  } catch (e) { out = String(e.stdout || '') + String(e.stderr || ''); }
  const ok = /GUI OK/.test(out);
  // 日志再核一遍：模型登记 + 无缺模型/缺贴图报错
  const dir = join('testgame', 'versions', inst, 'logs');
  let last = null;
  if (existsSync(dir)) {
    const files = readdirSync(dir).filter((f) => f.startsWith('mcver-launch-')).sort();
    if (files.length) last = files[files.length - 1];
  }
  let modelLine = false, bad = [];
  if (last) {
    const txt = readFileSync(join(dir, last), 'utf8');
    modelLine = txt.includes('mcver: model registered');
    bad = txt.split(/\r?\n/).filter((l) => /Unable to load model|Using missing texture/i.test(l)).slice(0, 2);
  }
  // 1.7.x 没有 ModelLoader（贴图走 setTextureName），所以只看配方
  const modelOk = mc.startsWith('1.7') ? true : modelLine;
  if (ok && modelOk && bad.length === 0) { console.log(`PASS ${mc}`); pass++; }
  else { console.log(`FAIL ${mc}  marker=${ok} model=${modelOk} bad=${bad.join(' | ')}`); fail++; }
}
console.log(`\n${pass} PASS / ${fail} FAIL / ${skip} SKIP`);
if (fail) process.exit(1);
