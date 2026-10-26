// 最后一批：箭头 switch（Java 14）→ 经典 switch，以及模式匹配 instanceof（Java 16）。
// 逐文件定点，改完靠 javac 兜底。
//
// 两个坑（第一版都踩了）：
//   1. 替换时如果把行尾的 \n 也吃掉，紧挨着的下一行就匹配不到了 → 用 lookahead (?=\n) 不收尾。
//   2. 有的文件是 CRLF，有的 LF → 统一先转成 LF 干活，写完再按原样还原，不然 `\{\n` 一个都匹配不上。
import fs from 'node:fs';

const SRC = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/';

function withLf(path, fn) {
  const raw = fs.readFileSync(path, 'utf8');
  const crlf = raw.includes('\r\n');
  const s = crlf ? raw.replace(/\r\n/g, '\n') : raw;
  const out = fn(s);
  fs.writeFileSync(path, Buffer.from(crlf ? out.replace(/\n/g, '\r\n') : out, 'utf8'));
  return { arrows: (out.match(/->/g) || []).length, crlf };
}

/** `case X -> { ... }` / `case X -> stmt;` → Java 8 经典 switch。 */
function classicSwitch(src) {
  let out = src;
  // 1) 带块的分支：case X -> {  ...  }（靠缩进配对）
  for (;;) {
    const m = /\n([ \t]*)case ([^:\n]+?) -> \{\n/.exec(out);
    if (!m) break;
    const indent = m[1];
    const startBody = m.index + m[0].length;
    const closeMark = '\n' + indent + '}';
    const end = out.indexOf(closeMark, startBody);
    if (end < 0) throw new Error('配对失败: ' + m[0]);
    const body = out.slice(startBody, end);
    const head = '\n' + indent + 'case ' + m[2] + ': {\n';
    out = out.slice(0, m.index) + head + body
        + '\n' + indent + '    break;' + closeMark + out.slice(end + closeMark.length);
  }
  // 2) 单语句分支
  out = out.replace(/\n([ \t]*)case ([^:\n]+?) -> ([^\n{]+);(?=\n)/g, '\n$1case $2: $3; break;');
  out = out.replace(/\n([ \t]*)default -> ([^\n{]+);(?=\n)/g, '\n$1default: $2; break;');
  out = out.replace(/\n([ \t]*)default -> \{ \}(?=\n)/g, '\n$1default: { } break;');
  return out;
}

for (const f of ['ui/DownloadPage.java', 'ui/VersionsPage.java']) {
  const r = withLf(SRC + f, classicSwitch);
  console.log(`${f}: 剩余箭头 ${r.arrows}（${r.crlf ? 'CRLF' : 'LF'}）`);
}

// ---------------------------------------------------------------- 模式匹配 instanceof
const fixes = [
  ['core/Launcher.java', [
    [`            if (o instanceof String s) {
                out.add(Installer.sub(s, ph));
            } else if (o instanceof Map<?, ?> raw) {
                Map<String, Object> rule = json_obj(raw);`,
     `            if (o instanceof String) {
                out.add(Installer.sub((String) o, ph));
            } else if (o instanceof Map<?, ?>) {
                Map<String, Object> rule = json_obj((Map<?, ?>) o);`],
    [`                if (v instanceof String s) out.add(Installer.sub(s, ph));`,
     `                if (v instanceof String) out.add(Installer.sub((String) v, ph));`],
    [`        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));`,
     `        return o instanceof Number ? ((Number) o).doubleValue() : Double.parseDouble(String.valueOf(o));`],
  ]],
];
for (const [f, pairs] of fixes) {
  let done = 0, miss = [];
  withLf(SRC + f, (s) => {
    for (const [a, b] of pairs) {
      if (!s.includes(a)) { miss.push(a.split('\n')[0]); continue; }
      s = s.split(a).join(b);
      done++;
    }
    return s;
  });
  console.log(`${f}: instanceof 改 ${done} 处${miss.length ? '，未命中 ' + miss.length : ''}`);
  for (const m of miss) console.log('    MISS ' + m);
}
