// Java 8 兼容收尾第二轮：javac 报出来的零散问题。
import fs from 'node:fs';

const SRC = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/';

function patch(file, pairs) {
  const p = SRC + file;
  const raw = fs.readFileSync(p, 'utf8');
  const crlf = raw.includes('\r\n');
  let s = crlf ? raw.replace(/\r\n/g, '\n') : raw;
  let hit = 0;
  for (const [a, b] of pairs) {
    if (!s.includes(a)) { console.log('  MISS ' + file + ' :: ' + a.split('\n')[0].slice(0, 70)); continue; }
    s = s.split(a).join(b);
    hit++;
  }
  fs.writeFileSync(p, Buffer.from(crlf ? s.replace(/\n/g, '\r\n') : s, 'utf8'));
  console.log(`  ${file}: ${hit}/${pairs.length}`);
}

const jobs = [
  // URLEncoder.encode 的 Charset 重载是 Java 10；Java 8 只有 (String, String)
  ['core/Auth.java', [
    ['URLEncoder.encode(s, StandardCharsets.UTF_8)', 'URLEncoder.encode(s, "UTF-8")'],
  ]],
  ['core/Modrinth.java', [
    ['URLEncoder.encode(s, StandardCharsets.UTF_8)', 'URLEncoder.encode(s, "UTF-8")'],
  ]],
  // 建 HttpClient 的残留
  ['core/Installer.java', [
    [`        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

`,
     ''],
    // Path::of 是 Java 11
    ['map(Path::of)', 'map(Paths::get)'],
    // Matcher.appendReplacement(StringBuilder, …) 是 Java 9；Java 8 只认 StringBuffer
    [`        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = ph.get(m.group(1));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();`,
     `        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = ph.get(m.group(1));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();`],
  ]],
  // 追加写日志：原来用 Files.writeString 的 APPEND 重载，改成 Jdk 的
  ['core/InstallJob.java', [
    [`            dev.mcver.core.Jdk.writeString(f,
                    "[" + java.time.LocalTime.now().withNano(0) + "] " + line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);`,
     `            dev.mcver.core.Jdk.writeAppend(f,
                    "[" + java.time.LocalTime.now().withNano(0) + "] " + line + System.lineSeparator());`],
  ]],
  ['ui/MainScreen.java', [
    [`        dev.mcver.core.Config.Account acc = Config.account();`,
     `        dev.mcver.core.Account acc = Config.account();`],
  ]],
  // readAllBytes 残留
  ['core/LoaderInstaller.java', [
    ['return new String(in.readAllBytes(), StandardCharsets.UTF_8);',
     'return dev.mcver.core.Jdk.readAllText(in);'],
  ]],
  ['core/JavaFinder.java', [
    ['String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);',
     'String out = dev.mcver.core.Jdk.readAllText(p.getInputStream());'],
  ]],
  // Process#pid() 是 Java 9
  ['ui/LaunchPage.java', [
    ['"hs_err_pid" + r.process.pid() + ".log"', '"hs_err_pid" + dev.mcver.core.Jdk.pid(r.process) + ".log"'],
    ['? "pid " + r.process.pid() +', '? "pid " + dev.mcver.core.Jdk.pid(r.process) +'],
    [': "pid " + r.process.pid() +', ': "pid " + dev.mcver.core.Jdk.pid(r.process) +'],
    ['"  pid=" + r.process.pid() +', '"  pid=" + dev.mcver.core.Jdk.pid(r.process) +'],
  ]],
];
for (const [f, pairs] of jobs) patch(f, pairs);

// MainScreen 299 的 writeString 多参调用
patch('ui/MainScreen.java', [
  [`            dev.mcver.core.Jdk.writeString(Paths.get("mcver-debug.txt").toAbsolutePath(), line,`,
   `            dev.mcver.core.Jdk.writeAppend(Paths.get("mcver-debug.txt").toAbsolutePath(), line`],
]);
