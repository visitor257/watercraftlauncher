// 一次性把「1.21.x 通用 jar」需要躲开的 API 差异全用 javap 查出来，不靠记忆。
// 对照 1.21 / 1.21.1 / 1.21.4 / 1.21.11 四个点。
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';

const BASE = 'C:\\Users\\Administrator.UX410U\\.gradle\\caches\\fabric-loom\\minecraftMaven\\net\\minecraft\\minecraft-merged';
const JAVAP = 'D:\\Program Files\\Java\\jdk-21.0.7\\bin\\javap.exe';

const dirs = fs.readdirSync(BASE).filter(n => n.startsWith('1.21'));
function jarFor(v) {
  const d = dirs.find(n => n.startsWith(v + '-net.fabricmc.yarn'));
  if (!d) return null;
  const f = fs.readdirSync(`${BASE}\\${d}`).find(x => x.endsWith('.jar') && !x.endsWith('.backup'));
  return f ? `${BASE}\\${d}\\${f}` : null;
}

const VERSIONS = ['1.21', '1.21.1', '1.21.4', '1.21.11'];
// 我们真正会调用/覆盖的成员
const TARGETS = [
  ['net.minecraft.client.gui.screen.Screen', /render|keyPressed|mouseScrolled|shouldPause|close|renderBackground/],
  ['net.minecraft.item.Item$Settings', /registryKey|maxCount/],
  ['net.minecraft.client.option.KeyBinding', /KeyBinding\(/],
  ['net.minecraft.client.util.ScreenshotRecorder', /saveScreenshot/],
  ['net.minecraft.client.option.GameOptions', /useKey/],
  ['net.minecraft.util.ActionResult', /PASS|SUCCESS|CONSUME/],
  ['net.minecraft.util.TypedActionResult', /./],
  ['net.fabricmc.fabric.api.event.player.UseItemCallback', /./],
];

const out = {};
for (const v of VERSIONS) {
  const jar = jarFor(v);
  if (!jar) { console.log(`!! ${v} 没 jar`); continue; }
  out[v] = {};
  for (const [cls, re] of TARGETS) {
    let txt = '';
    try {
      txt = execFileSync(JAVAP, ['-cp', jar, cls], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
    } catch (e) {
      out[v][cls] = ['<不存在>'];
      continue;
    }
    const lines = txt.split('\n')
      .map(l => l.trim())
      .filter(l => l && !l.startsWith('Compiled from') && !l.startsWith('}'))
      .filter(l => re.test(l))
      .map(l => l.replace(/\s+/g, ' '));
    out[v][cls] = lines;
  }
}

for (const [cls] of TARGETS) {
  console.log(`\n########## ${cls}`);
  for (const v of VERSIONS) {
    const lines = out[v]?.[cls];
    if (!lines) continue;
    console.log(`  --- ${v}: ${lines.length === 1 && lines[0] === '<不存在>' ? '不存在' : ''}`);
    for (const l of lines) console.log(`      ${l}`);
  }
}

fs.writeFileSync('D:/Development/watercraftlauncher/logs/build/121-api-diff.json', JSON.stringify(out, null, 2));
console.log('\n-> logs/build/121-api-diff.json');
