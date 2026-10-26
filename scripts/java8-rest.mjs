// 收尾：把剩下「语法级」的 Java 14+ 写法改成 Java 8。
// 这些都是穷举式的定点替换，改完编译器会兜底，所以不必写成通用转换器。
import fs from 'node:fs';

const SRC = 'D:/Development/watercraftlauncher/mod/src/';

const edits = [
  // ---------------- var → 具体类型（JDK 类型一律写全限定名，省得每个文件都要加 import）
  ['main/java/dev/mcver/core/Dirs.java', [
    ['try (var ds = Files.newDirectoryStream(dir)) {',
     'try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(dir)) {'],
  ]],
  ['main/java/dev/mcver/core/Installer.java', [
    ['var m = PH.matcher(s);', 'java.util.regex.Matcher m = PH.matcher(s);'],
  ]],
  ['main/java/dev/mcver/core/JavaFinder.java', [
    ['try (var ds = Files.list(root)) {', 'try (java.util.stream.Stream<java.nio.file.Path> ds = Files.list(root)) {'],
    ['var m = java.util.regex.Pattern.compile("version \\"([^\\"]+)\\"")',
     'java.util.regex.Matcher m = java.util.regex.Pattern.compile("version \\"([^\\"]+)\\"")'],
  ]],
  ['main/java/dev/mcver/core/JavaRuntime.java', [
    ['try (var w = Files.walk(rt, 8)) {', 'try (java.util.stream.Stream<java.nio.file.Path> w = Files.walk(rt, 8)) {'],
  ]],
  ['main/java/dev/mcver/core/Launcher.java', [
    ['try (var s = Files.walk(root)) {', 'try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(root)) {'],
    ['try (var s = Files.list(versions)) {', 'try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(versions)) {'],
    ['try (var s = Files.list(dir)) {', 'try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(dir)) {'],
    ['return s.findAny().isEmpty();', 'return !s.findAny().isPresent();'],
  ]],
  ['main/java/dev/mcver/core/LoaderInstaller.java', [
    ['try (var ds = Files.list(dir)) {', 'try (java.util.stream.Stream<java.nio.file.Path> ds = Files.list(dir)) {'],
    ['try (var in = Files.newInputStream(f)) {', 'try (java.io.InputStream in = Files.newInputStream(f)) {'],
    ['try (var in = z.getInputStream(e)) {', 'try (java.io.InputStream in = z.getInputStream(e)) {'],
    ['try (var in = LoaderInstaller.class.getResourceAsStream("/optifine-runner.jar")) {',
     'try (java.io.InputStream in = LoaderInstaller.class.getResourceAsStream("/optifine-runner.jar")) {'],
    ['var list = LoaderVersions.fetch(Kind.FORGE, mc);', 'java.util.List<LoaderVersions.Lv> list = LoaderVersions.fetch(Kind.FORGE, mc);'],
    ['var list = LoaderVersions.optifineList(mc);', 'java.util.List<LoaderVersions.Lv> list = LoaderVersions.optifineList(mc);'],
    ['var md = java.security.MessageDigest.getInstance("SHA-1");',
     'java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");'],
    // —— 文本块（Java 15）→ 普通字符串拼接
    ['        String json = """\n                {\n                  "profiles": {},\n                  "settings": { "enableSnapshots": false },\n                  "version": 3\n                }\n                """;',
     '        String json = "{\\n"\n                + "  \\"profiles\\": {},\\n"\n                + "  \\"settings\\": { \\"enableSnapshots\\": false },\\n"\n                + "  \\"version\\": 3\\n"\n                + "}\\n";'],
  ]],
  ['main/java/dev/mcver/ui/LaunchPage.java', [
    ['var s = MinecraftClient.getInstance().getSession();\n        Account acc = Config.account();',
     'Account acc = Config.account();'],
    ['player = s.getUsername();', 'player = dev.mcver.core.Compat.sessionName();'],
    ['token = s.getAccessToken();', 'token = dev.mcver.core.Compat.sessionToken();'],
  ]],
  ['main/java/dev/mcver/ui/MainScreen.java', [
    ['var sid = dev.mcver.core.Compat.itemId(st.getItem());',
     'Object sid = dev.mcver.core.Compat.itemId(st.getItem());'],
    ['var acc = Config.account();', 'dev.mcver.core.Config.Account acc = Config.account();'],
  ]],
  ['main/java/dev/mcver/ui/VersionsPage.java', [
    ['try (var s = Files.walk(root)) {', 'try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(root)) {'],
    ['try (var s = Files.list(logs)) {', 'try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(logs)) {'],
  ]],
  // compat 里的 var：这些文件是 target 专属的，可以直接写具体类型
  ['compat/1.16.5/java/dev/mcver/core/Compat.java', [
    ['var session = MinecraftClient.getInstance().getSession();',
     'net.minecraft.client.util.Session session = MinecraftClient.getInstance().getSession();'],
    ['var mc = MinecraftClient.getInstance();', 'MinecraftClient mc = MinecraftClient.getInstance();'],
  ]],
  ['compat/1.17.1/java/dev/mcver/core/Compat.java', [
    ['var session = MinecraftClient.getInstance().getSession();',
     'net.minecraft.client.util.Session session = MinecraftClient.getInstance().getSession();'],
    ['var mc = MinecraftClient.getInstance();', 'MinecraftClient mc = MinecraftClient.getInstance();'],
  ]],
  ['compat/1.18.2/java/dev/mcver/core/Compat.java', [
    ['var session = MinecraftClient.getInstance().getSession();',
     'net.minecraft.client.util.Session session = MinecraftClient.getInstance().getSession();'],
    ['var mc = MinecraftClient.getInstance();', 'MinecraftClient mc = MinecraftClient.getInstance();'],
  ]],
];

let ok = 0, miss = [];
for (const [file, pairs] of edits) {
  const p = SRC + file;
  let s = fs.readFileSync(p, 'utf8');
  for (const [a, b] of pairs) {
    if (!s.includes(a)) { miss.push(file + ' :: ' + a.split('\n')[0].slice(0, 70)); continue; }
    s = s.split(a).join(b);
    ok++;
  }
  fs.writeFileSync(p, Buffer.from(s, 'utf8'));
}
console.log(`命中 ${ok} 处`);
if (miss.length) { console.log('未命中：'); for (const m of miss) console.log('  ' + m); }
