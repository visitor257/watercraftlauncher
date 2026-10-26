// 试验：把 1.21.1 target 的 jar 版本区间放开到 >=1.21 <1.21.2，看它在 1.21 上跑不跑得起来。
// 目的：判断「同一 API 时代共用一个 jar」这条路是不是真的便宜（决定还要不要为每个版本建 target）。
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const jar = 'D:/Development/watercraftlauncher/dist/watercraftlauncher-fabric-1.0.0-mc1.21.1.jar';
const work = 'D:/Development/watercraftlauncher/testgame/work/jar121';
fs.rmSync(work, { recursive: true, force: true });
fs.mkdirSync(work, { recursive: true });

execFileSync('C:/Program Files/Java/jdk-21.0.7/bin/jar', ['xf', jar], { cwd: work });

const fmPath = path.join(work, 'fabric.mod.json');
const fm = JSON.parse(fs.readFileSync(fmPath, 'utf8'));
console.log('原区间:', fm.depends.minecraft);
fm.depends.minecraft = '>=1.21 <1.21.2';
fs.writeFileSync(fmPath, JSON.stringify(fm, null, 2), 'utf8');
console.log('新区间:', fm.depends.minecraft);

const out = 'D:/Development/watercraftlauncher/testgame/work/wcl-1.21.jar';
execFileSync('C:/Program Files/Java/jdk-21.0.7/bin/jar', ['cfM', out, '-C', work, '.'], { stdio: 'inherit' });
console.log('产物:', out, fs.statSync(out).size, 'bytes');
