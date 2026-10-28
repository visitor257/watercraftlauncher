// 小工具：javap 某个类并过滤行。避开 PowerShell 里 $_ 为空报错的老坑。
// 用法： node modx/jp.mjs <jar> <class> [regex]
import { execFileSync } from 'node:child_process';

const [, , jar, cls, re] = process.argv;
const out = execFileSync('D:\\Program Files\\Java\\jdk-21.0.7\\bin\\javap.exe', ['-p', '-classpath', jar, cls], {
  encoding: 'utf8',
  maxBuffer: 1 << 26,
});
const rx = re ? new RegExp(re) : null;
for (const line of out.split(/\r?\n/)) {
  if (!line.trim()) continue;
  if (rx && !rx.test(line)) continue;
  console.log(line.trim());
}
