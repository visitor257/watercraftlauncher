// 把 record（Java 16）改写成 Java 8 的 final class。
// 保留「public final 字段 + 同名无参方法」双重形式，这样 found.exe 和 found.exe() 两种写法都不用改调用点。
// 同时补上 equals/hashCode/toString，语义跟 record 保持一致，省得以后有人靠值相等去比对。
import fs from 'node:fs';

const J = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/core/';

/** [文件, record 原始声明（含尾随 ' { }' 或 '{'）, 类名, 可见性, [[类型, 名字], ...], 额外方法体(原样)] */
const defs = [
  ['JavaFinder.java', 'public record Found(Path exe, int major, String raw, boolean custom, String arch) {',
    'Found', 'public static final class',
    [['Path', 'exe'], ['int', 'major'], ['String', 'raw'], ['boolean', 'custom'], ['String', 'arch']]],
  ['JavaFinder.java', 'record Probe(int major, String arch) { }',
    'Probe', 'static final class',
    [['int', 'major'], ['String', 'arch']]],
  ['JavaRuntime.java', 'record Job(String url, Path dst, boolean exec) { }',
    'Job', 'static final class',
    [['String', 'url'], ['Path', 'dst'], ['boolean', 'exec']]],
  ['LoaderInstaller.java', 'private record Fetch(String rel, List<String> urls, String sha1, long size) { }',
    'Fetch', 'private static final class',
    [['String', 'rel'], ['List<String>', 'urls'], ['String', 'sha1'], ['long', 'size']]],
  ['LoaderVersions.java', 'public record Lv(String id, String note) {',
    'Lv', 'public static final class',
    [['String', 'id'], ['String', 'note']]],
  ['Modrinth.java', 'public record File(String url, String filename) { }',
    'File', 'public static final class',
    [['String', 'url'], ['String', 'filename']]],
  ['Modrinth.java', 'public record Ver(String id, String number, String channel, List<File> files) { }',
    'Ver', 'public static final class',
    [['String', 'id'], ['String', 'number'], ['String', 'channel'], ['List<File>', 'files']]],
  ['Sources.java', 'public record Src(String name, String root) {',
    'Src', 'public static final class',
    [['String', 'name'], ['String', 'root']]],
  ['VersionList.java', 'public record V(String id, String type, String releaseTime, String url) {',
    'V', 'public static final class',
    [['String', 'id'], ['String', 'type'], ['String', 'releaseTime'], ['String', 'url']]],
];

function build(cls, vis, comps) {
  const args = comps.map(c => c[0] + ' ' + c[1]).join(', ');
  const fields = comps.map(c => `        public final ${c[0]} ${c[1]};`).join('\n');
  const assign = comps.map(c => `            this.${c[1]} = ${c[1]};`).join('\n');
  const acc = comps.map(c => `        public ${c[0]} ${c[1]}() { return ${c[1]}; }`).join('\n');
  const eq = comps.map(c => {
    const primitive = ['int', 'long', 'boolean', 'double', 'float'].includes(c[0]);
    return primitive
      ? `                && ${c[1]} == that.${c[1]}`
      : `                && java.util.Objects.equals(${c[1]}, that.${c[1]})`;
  }).join('\n');
  const hash = comps.map(c => {
    const primitive = ['int', 'long', 'boolean', 'double', 'float'].includes(c[0]);
    return primitive ? `                ${c[1]}` : `                java.util.Objects.hashCode(${c[1]})`;
  }).join(',\n');
  const str = comps.map(c => `"${c[1]}=" + ${c[1]}`).join(' + ", " + ');
  return `${vis} ${cls} {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
${fields}

        public ${cls}(${args}) {
${assign}
        }

${acc}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ${cls})) return false;
            ${cls} that = (${cls}) o;
            return true
${eq};
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
${hash});
        }

        @Override
        public String toString() {
            return "${cls}[" + ${str} + "]";
        }
`;
}

let n = 0;
for (const [file, decl, cls, vis, comps] of defs) {
  const p = J + file;
  let s = fs.readFileSync(p, 'utf8');
  if (!s.includes(decl)) { console.log('!! 找不到声明: ' + file + ' :: ' + decl); continue; }
  // record 声明后面可能是 " }" 结尾（空 record）或 "{ ... }" 带方法体；
  // 统一把 "{" 换成类头 + 构造器，把原有的方法体接在后面，原有的孤立 "}" 由 build() 收尾。
  const head = build(cls, vis, comps);
  if (decl.endsWith('{ }')) {
    s = s.replace(decl, head + '    }');
  } else {
    s = s.replace(decl, head + '\n        // —— 以下为原 record 里的自定义方法\n');
  }
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
  console.log('  ' + cls + '  ok');
  n++;
}
console.log(`\n改写 ${n} 个 record`);
