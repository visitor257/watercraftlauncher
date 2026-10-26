// 把主源码里「Java 9+ 的 API」机械替换成 Java 8 能编的写法。
// 只做纯文本替换，不改结构；语法层面的（var / 箭头 switch / 模式 instanceof）在 java8-syntax.mjs 里。
import fs from 'node:fs';
import path from 'node:path';

const root = 'D:/Development/watercraftlauncher/mod/src';
const files = [];
(function walk(d) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.java')) files.push(p);
  }
})(root);

const rules = [
  // —— String#isBlank()（Java 11）
  [/(\.[A-Za-z_$][\w$]*\(\)|[\w$.\[\]]+)\.isBlank\(\)/g, '$1.trim().isEmpty()'],
  // 上面那条对 foo().isBlank() 这类不够，单独再兜一遍
  [/\)\.isBlank\(\)/g, ').trim().isEmpty()'],
  [/\.isBlank\(\)/g, '.trim().isEmpty()'],

  // —— Files.writeString / readString（Java 11）→ Jdk 垫片
  [/Files\.writeString\(/g, 'dev.mcver.core.Jdk.writeString('],
  [/Files\.readString\(/g, 'dev.mcver.core.Jdk.readString('],

  // —— List.of() / Map.of()（Java 9）→ 空集合（源码里全部是无参用法）
  [/List\.of\(\)/g, 'dev.mcver.core.Jdk.emptyList()'],
  [/Map\.of\(\)/g, 'dev.mcver.core.Jdk.emptyMap()'],

  // —— ProcessHandle（Java 9）
  [/ProcessHandle\.current\(\)\.info\(\)\.command\(\)/g, 'java.util.Optional.ofNullable(dev.mcver.core.Jdk.currentExe()).map(java.nio.file.Path::toString)'],

  // —— Stream#toList()（Java 16）
  [/\.toList\(\)/g, '.collect(java.util.stream.Collectors.toList())'],
];

let changed = 0, edits = 0;
for (const f of files) {
  let s = fs.readFileSync(f, 'utf8');
  const before = s;
  for (const [re, to] of rules) {
    const m = s.match(re);
    if (m) { edits += m.length; s = s.replace(re, to); }
  }
  // 反复叠出来的（比如 .trim().isEmpty() 被再替换）不会发生，但要防 Jdk. 被重复包
  s = s.replace(/dev\.mcver\.core\.Jdk\.writeString\(\s*dev\.mcver\.core\.Jdk\./g, 'dev.mcver.core.Jdk.writeString(');
  if (s !== before) {
    fs.writeFileSync(f, Buffer.from(s, 'utf8'));
    changed++;
    console.log('  ' + path.relative(root, f));
  }
}
console.log(`\n改写 ${changed} 个文件，替换 ${edits} 处`);
