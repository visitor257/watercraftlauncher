// 最后一刀：把 java.net.http.HttpClient（Java 11）全部换成 Net（HttpURLConnection）。
//
// 为什么必须换：1.16.5 要 -Pjava_release=8，java.net.http 这个包在 Java 8 上根本不存在。
// 顺带好处是统一到一个实现 —— Net 的超时是真超时（HttpClient 在游戏 JVM 里曾卡 7 分钟）。
import fs from 'node:fs';

const C = 'D:/Development/watercraftlauncher/mod/src/main/java/dev/mcver/core/';

function withLf(path, fn) {
  const raw = fs.readFileSync(path, 'utf8');
  const crlf = raw.includes('\r\n');
  const s = crlf ? raw.replace(/\r\n/g, '\n') : raw;
  const out = fn(s);
  fs.writeFileSync(path, Buffer.from(crlf ? out.replace(/\n/g, '\r\n') : out, 'utf8'));
}

const jobs = [
  // ---------------------------------------------------------------- Net：补 Java 8 读流 + POST 支持
  ['Net.java', (s) => {
    s = s.replace('return new String(in.readAllBytes(), StandardCharsets.UTF_8);',
                  'return new String(Jdk.readAll(in), StandardCharsets.UTF_8);');
    // 在 isJar 之前插入 POST / JSON 辅助方法
    const anchor = '    /** jar 是不是完整的（能不能当 zip 打开）。下载中途断掉的文件会在这里被拓出来。 */';
    const add = `    // ---------------------------------------------------------------- POST / JSON
    // Auth 那几个接口要 POST。原来用 java.net.http.HttpRequest，Java 8 上没有，这里补上等价物。

    /** 一次请求的结果：状态码 + 正文（小响应，几十 KB 以内）。 */
    public static final class Resp {
        public final int status;
        public final String text;

        Resp(int status, String text) {
            this.status = status;
            this.text = text;
        }

        public boolean ok() { return status / 100 == 2; }

        @Override
        public String toString() { return "HTTP " + status; }
    }

    /** application/x-www-form-urlencoded 的 POST。 */
    public static Resp postForm(String url, String form, int connectMs, int readMs) throws IOException {
        return request("POST", url, form.getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded", null, connectMs, readMs);
    }

    /** application/json 的 POST；bearer 传 null 就不带 Authorization。 */
    public static Resp postJson(String url, String body, String bearer, int connectMs, int readMs) throws IOException {
        return request("POST", url, body.getBytes(StandardCharsets.UTF_8),
                "application/json", bearer, connectMs, readMs);
    }

    /** 带 Authorization 的 GET。 */
    public static Resp getJson(String url, String bearer, int connectMs, int readMs) throws IOException {
        return request("GET", url, null, null, bearer, connectMs, readMs);
    }

    private static Resp request(String method, String url, byte[] body, String contentType,
                                String bearer, int connectMs, int readMs) throws IOException {
        HttpURLConnection c = open(url, connectMs, readMs);
        try {
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (contentType != null) c.setRequestProperty("Content-Type", contentType);
            if (bearer != null) c.setRequestProperty("Authorization", "Bearer " + bearer);
            if (body != null) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code / 100 == 2 ? c.getInputStream() : c.getErrorStream();
            String text = "";
            if (in != null) {
                try (InputStream i = in) {
                    text = new String(Jdk.readAll(i), StandardCharsets.UTF_8);
                }
            }
            return new Resp(code, text);
        } finally {
            c.disconnect();
        }
    }

${anchor}`;
    return s.replace(anchor, add);
  }],

  // ---------------------------------------------------------------- Installer：去掉 HttpClient 参数
  ['Installer.java', (s) => {
    s = s.replace(/import java\.net\.http\.HttpClient;\n/g, '')
         .replace(/import java\.net\.http\.HttpRequest;\n/g, '')
         .replace(/import java\.net\.http\.HttpResponse;\n/g, '');
    // 建 HttpClient 的那几行删掉
    s = s.replace(/        HttpClient http = HttpClient\.newBuilder\(\)\n(\s+\.followRedirects\(HttpClient\.Redirect\.NORMAL\)\n)?(\s+\.connectTimeout\([^)]*\)\n)?(\s+\.build\(\);\n)/, '');
    s = s.replace(/getString\(http, /g, 'getString(');
    s = s.replace(/downloadAll\(http, /g, 'downloadAll(');
    s = s.replace(/downloadAssets\(http, /g, 'downloadAssets(');
    s = s.replace(/static void downloadAll\(HttpClient http, /g, 'static void downloadAll(');
    s = s.replace(/static void downloadAssets\(HttpClient http, /g, 'static void downloadAssets(');
    s = s.replace(/static void fetchTo\(HttpClient http, /g, 'static void fetchTo(');
    s = s.replace(/static String getString\(HttpClient http, /g, 'static String getString(');
    s = s.replace(/fetchTo\(http, /g, 'fetchTo(');
    // fetchTo 本体：直接用 Net.download（它已经有 .part + 重试 + 校验）
    s = s.replace(
`        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2)
                    throw new IOException("HTTP " + resp.statusCode() + " " + url);
                try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(part)) {
                    in.transferTo(out);
                }`,
`        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Net.download(url, part, 20_000, 300_000);`);
    // getString 本体
    s = s.replace(
`                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
                return resp.body();`,
`                return Net.get(url, 15_000, 60_000);`);
    return s;
  }],

  // ---------------------------------------------------------------- Auth：postForm / postJson / getJson / send
  ['Auth.java', (s) => {
    s = s.replace(/import java\.net\.http\.HttpClient;\n/g, '')
         .replace(/import java\.net\.http\.HttpRequest;\n/g, '')
         .replace(/import java\.net\.http\.HttpResponse;\n/g, '');
    s = s.replace(/    private static final HttpClient HTTP = HttpClient\.newBuilder\(\)\n(\s+\.[^\n]*\n)*?\s+\.build\(\);\n\n?/, '');
    const from = '    private static Map<String, Object> postForm(String url, String form) throws Exception {';
    const idx = s.indexOf(from);
    if (idx < 0) throw new Error('Auth 结构变了');
    const endMark = '\n    }\n';
    // send() 是这一段的最后一个方法，直接把它整段替换成语义等价的 Net 版本
    const sendIdx = s.indexOf('    private static Map<String, Object> send(HttpRequest req)');
    if (sendIdx < 0) throw new Error('Auth.send 找不到');
    const closeMark = '\n    }\n';
    const sendEnd = s.indexOf(closeMark, sendIdx) + closeMark.length;
    const head = `    private static Map<String, Object> postForm(String url, String form) throws Exception {
        return wrap(Net.postForm(url, form, 15_000, 30_000));
    }

    private static Map<String, Object> postJson(String url, String body, String bearer) throws Exception {
        return wrap(Net.postJson(url, body, bearer, 15_000, 30_000));
    }

    private static Map<String, Object> getJson(String url, String bearer) throws Exception {
        return wrap(Net.getJson(url, bearer, 15_000, 30_000));
    }

    /** 统一成「json 对象 + __status」，调用方原来就是这么用的。 */
    private static Map<String, Object> wrap(Net.Resp r) {
        Map<String, Object> m;
        try {
            m = new LinkedHashMap<>(Json.obj(Json.parse(r.text)));
        } catch (Exception e) {
            m = new LinkedHashMap<>();
        }
        m.put("__status", r.status);
        return m;
    }
`;
    return s.slice(0, idx) + head + s.slice(sendEnd);
  }],

  // ---------------------------------------------------------------- 只有声明没人用的 HttpClient，删掉
  ['Modrinth.java', (s) => s
    .replace(/import java\.net\.http\.[A-Za-z]+;\n/g, '')
    .replace(/    private static final HttpClient HTTP = HttpClient\.newBuilder\(\)\n(\s+\.[^\n]*\n)*?\s+\.build\(\);\n\n?/, '')],
  ['LoaderVersions.java', (s) => s
    .replace(/import java\.net\.http\.[A-Za-z]+;\n/g, '')
    .replace(/    private static final HttpClient HTTP = HttpClient\.newBuilder\(\)\n(\s+\.[^\n]*\n)*?\s+\.build\(\);\n\n?/, '')],
  ['LoaderInstaller.java', (s) => s
    .replace(/import java\.net\.http\.[A-Za-z]+;\n/g, '')
    .replace(/    private static final HttpClient HTTP = HttpClient\.newBuilder\(\)\n(\s+\.[^\n]*\n)*?\s+\.build\(\);\n\n?/, '')],
];

for (const [f, fn] of jobs) {
  withLf(C + f, fn);
  console.log('  ' + f);
}
console.log('done');
