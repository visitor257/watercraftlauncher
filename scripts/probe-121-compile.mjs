// 对 1.21.x 里从没编译过的版本逐版硬编译（只用主源码，不带 compat），拿编译器判决。
// 目的：确认主源码在 1.21~1.21.11 上到底哪些版本能直接编过 —— 能编过的，就说明 API 面没变。
import { spawn } from 'node:child_process';
import fs from 'node:fs';

const GRADLE = 'C:\\Users\\Administrator.UX410U\\.gradle\\wrapper\\dists\\gradle-8.14.2-bin\\2pb3mgt1p815evrl3weanttgr\\gradle-8.14.2\\bin\\gradle.bat';
const MOD = 'D:\\Development\\watercraftlauncher\\mod';
const LOG = 'D:\\Development\\watercraftlauncher\\logs\\build\\';

const meta = JSON.parse(fs.readFileSync(LOG + '121-meta.json', 'utf8'));
// 只跑从没编过的
const TODO = ['1.21', '1.21.2', '1.21.3', '1.21.5', '1.21.6', '1.21.7', '1.21.8'];

function run(args, logFile) {
  return new Promise((resolve) => {
    const out = fs.createWriteStream(logFile);
    const p = spawn(GRADLE, args, {
      cwd: MOD,
      shell: true,
      env: { ...process.env, JAVA_HOME: 'D:\\Program Files\\Java\\jdk-21.0.7', GRADLE_OPTS: '-Duser.language=en -Duser.country=US' },
    });
    let buf = '';
    p.stdout.on('data', d => { out.write(d); buf += d; });
    p.stderr.on('data', d => { out.write(d); buf += d; });
    p.on('close', code => { out.end(); resolve({ code, buf }); });
  });
}

const results = [];
for (const v of TODO) {
  const m = meta.find(x => x.v === v);
  if (!m) { console.log(`${v}: 没元数据，跳`); continue; }
  const args = [
    'compileJava', '--console=plain',
    `-Pminecraft_version=${v}`,
    `-Pyarn_mappings=${m.yarn}`,
    `-Pfabric_api_version=${m.api}`,
    '-Pjava_release=21',
  ];
  const t0 = Date.now();
  const { code, buf } = await run(args, `${LOG}cc-${v}.log`);
  const secs = Math.round((Date.now() - t0) / 1000);
  const errors = [...buf.matchAll(/^.*?error: .*$/gm)].map(x => x[0].trim());
  results.push({ v, code, secs, errors: errors.slice(0, 12) });
  console.log(`\n===== ${v}  exit=${code}  ${secs}s  错误 ${errors.length} 条`);
  for (const e of errors.slice(0, 8)) console.log('   ' + e.slice(0, 160));
}

fs.writeFileSync(LOG + '121-compile.json', JSON.stringify(results, null, 2));
console.log('\n汇总：');
for (const r of results) console.log(`  ${r.v.padEnd(9)} exit=${r.code} 错误=${r.errors.length}`);
