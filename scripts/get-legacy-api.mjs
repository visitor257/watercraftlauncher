// 找 1.12.2 能用的 Legacy Fabric API：按 Modrinth 查，下到 modx/legacy1122，打印 fabric.mod.json 的依赖
import { writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

const W = 'D:/Development/watercraftlauncher/modx/legacy1122';
mkdirSync(W, { recursive: true });

const url = 'https://api.modrinth.com/v2/project/legacy-fabric-api/version?game_versions=%5B%221.12.2%22%5D';
const list = await (await fetch(url, { headers: { 'User-Agent': 'wcwl/1.0' } })).json();
console.log('候选', list.length);
for (const v of list.slice(0, 6)) {
  console.log(v.version_number, v.version_type, v.files[0].filename, Math.round(v.files[0].size / 1024) + 'KB', v.game_versions.join('/'));
}
// 取最新的 release
const rel = list.filter((v) => v.version_type === 'release')[0] || list[0];
const dest = join(W, rel.files[0].filename);
const buf = Buffer.from(await (await fetch(rel.files[0].url)).arrayBuffer());
writeFileSync(dest, buf);
console.log('\n下载', dest, buf.length);

execFileSync('jar', ['xf', dest, 'fabric.mod.json'], { cwd: W, stdio: 'inherit' });
console.log(execFileSync('cmd', ['/c', 'type', join(W, 'fabric.mod.json')], { encoding: 'utf8' }).slice(0, 800));
