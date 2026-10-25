package dev.mcver.core;

// McVerCore / Installer.java
// 把任意一个官方 Minecraft 版本完整安装到指定 .minecraft 目录。
// 无第三方依赖，Java 17+，可直接 `java Installer.java <gameDir> <version> [flags]` 运行。
//
// 用法:
//   java Installer.java D:\mc 1.21.4 --mirror
//   java Installer.java D:\mc 1.7.10 --plan
//   java Installer.java D:\mc 1.20.1 --no-assets
//
// flags:
//   --mirror     走 BMCLAPI 镜像（国内快）
//   --no-assets  跳过资源文件（库里已有 assets 时用）
//   --plan       只解析并打印计划，不下载
//   --jobs N     并发数，默认 16

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Installer {

    // 下载源已抽到 Sources（官方 + 用户自定义镜像），这里只留个引用

    /** 进度回调。 */
    public interface Progress {
        void on(String stage, long done, long total, String detail);
        Progress SILENT = (s, d, t, m) -> { };
    }

    public static final String MANIFEST_OFFICIAL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    public static final String MANIFEST_MIRROR =
            "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";

    static final String OS_NAME = detectOs();

    /** 归一化后的当前架构：x86_64 / x86 / arm64（其它原样返回）。 */
    static final String OS_ARCH = detectArch();

    private static String detectArch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (a) {
            case "amd64", "x86_64", "x86-64" -> "x86_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "aarch64", "arm64" -> "arm64";
            default -> a;
        };
    }

    /** json 里的 os.arch 和当前架构对不对得上。认不出来的 arch 一律放行。 */
    static boolean archMatches(String want) {
        String w = want.toLowerCase(Locale.ROOT);
        return switch (w) {
            case "x86", "i386", "i686" -> "x86".equals(OS_ARCH);
            case "x86_64", "amd64" -> "x86_64".equals(OS_ARCH);
            case "arm64", "aarch64" -> "arm64".equals(OS_ARCH);
            default -> true;
        };
    }

    // ---------------------------------------------------------------- 结果

    /** 一次安装的完整产物，交给 Relauncher 用。 */
    public static final class Installed {
        public Path gameDir;
        public String versionId;
        public String mainClass;
        public String assetIndex;
        public String assetIndexId;
        public Path clientJar;
        public Path nativesDir;
        public List<Path> classpath = new ArrayList<>();
        public List<String> jvmArgs = new ArrayList<>();
        public List<String> gameArgs = new ArrayList<>();
        public int requiredJava = 8;
        public String versionType = "release";
        public boolean legacyAssets;
        /** 版本 json 里的 javaVersion.component（Mojang 运行时组件名，如 java-runtime-epsilon） */
        public String javaComponent;
        /** 原始版本 json，Relauncher 需要它来重新求值 arguments */
        public Map<String, Object> versionJson;
    }

    // ---------------------------------------------------------------- 入口

    public static void main(String[] args) throws Exception {
        Path gameDir = Paths.get(".minecraft");
        String version = null;
        Sources.Src src = Sources.OFFICIAL;
        boolean assets = true, plan = false, printCmd = false;
        int jobs = 16;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mirror" -> src = Sources.BMCLAPI;
                case "--no-assets" -> assets = false;
                case "--plan" -> plan = true;
                case "--print-cmd" -> printCmd = true;
                case "--jobs" -> jobs = Integer.parseInt(args[++i]);
                default -> {
                    if (version == null) version = args[i];
                    else gameDir = Paths.get(args[i]);
                }
            }
        }
        if (version == null) {
            System.err.println("用法: java Installer.java <version> [gameDir] [--mirror] [--no-assets] [--plan]");
            System.exit(2);
        }

        Progress log = (stage, done, total, detail) -> {
            String line = total > 0
                    ? String.format("[%-8s] %d/%d %s", stage, done, total, detail)
                    : String.format("[%-8s] %s", stage, detail);
            System.out.println(line);
        };

        Installed r = install(gameDir, version, src, assets, plan, jobs, log);
        if (printCmd) {
            StringBuilder sb = new StringBuilder("java");
            for (String a : r.jvmArgs) sb.append(' ').append(quote(a));
            sb.append(' ').append(r.mainClass);
            for (String a : r.gameArgs) sb.append(' ').append(quote(a));
            System.out.println();
            System.out.println("---- launch command ----");
            System.out.println(sb);
            System.out.println("------------------------");
        }
        System.out.println();
        System.out.println("version      = " + r.versionId + " (" + r.versionType + ")");
        System.out.println("mainClass    = " + r.mainClass);
        System.out.println("requiredJava = " + r.requiredJava);
        System.out.println("classpath    = " + r.classpath.size() + " entries");
        System.out.println("natives      = " + r.nativesDir);
        System.out.println("assetsIndex  = " + r.assetIndexId);
        System.out.println("gameArgs     = " + r.gameArgs.size());
        System.out.println("jvmArgs      = " + r.jvmArgs.size());
    }

    // ---------------------------------------------------------------- 主流程

    public static Installed install(Path gameDir, String versionId, Sources.Src src,
                                    boolean withAssets, boolean planOnly, int jobs,
                                    Progress p) throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        // 1) 版本清单
        p.on("manifest", 0, 0, "获取版本清单 (" + src + ")");
        String manifestUrl = src == null ? MANIFEST_OFFICIAL : src.manifestUrl();
        Map<String, Object> manifest = Json.obj(Json.parse(getString(http, manifestUrl)));
        Map<String, Object> entry = null;
        for (Object o : Json.arrOrEmpty(manifest.get("versions"))) {
            Map<String, Object> v = Json.obj(o);
            if (versionId.equals(v.get("id"))) { entry = v; break; }
        }
        if (entry == null) throw new IllegalArgumentException("版本不存在: " + versionId);

        // 2) 版本 json
        p.on("version", 0, 0, "下载 " + versionId + ".json");
        String vUrl = rewrite(Json.str(entry.get("url")), src);
        String vJson = getString(http, vUrl);
        Map<String, Object> vj = Json.obj(Json.parse(vJson));

        Path verDir = gameDir.resolve("versions").resolve(versionId);
        boolean wasComplete = Launcher.isComplete(gameDir, versionId);
        Files.createDirectories(verDir);
        // 新装 / 之前就没装完的：先摆标记，全跑完才摘。
        // 已经在用的完整版本重装（补全）时不摆，免得失败把好版本藏了。
        if (!wasComplete) Launcher.markIncomplete(verDir);
        Files.writeString(verDir.resolve(versionId + ".json"), vJson, StandardCharsets.UTF_8);

        Installed r = new Installed();
        r.gameDir = gameDir;
        r.versionId = versionId;
        r.versionJson = vj;
        r.mainClass = Json.str(vj.get("mainClass"));
        r.versionType = vj.get("type") == null ? "release" : Json.str(vj.get("type"));
        Map<String, Object> javaVer = Json.objOrNull(vj.get("javaVersion"));
        if (javaVer != null) {
            if (javaVer.get("majorVersion") != null)
                r.requiredJava = (int) Json.num(javaVer.get("majorVersion"));
            r.javaComponent = Json.str((Object) javaVer.get("component"));
        }

        // 3) client jar
        Map<String, Object> dl = Json.objOrNull(vj.get("downloads"));
        Map<String, Object> clientObj = Json.objOrNull(dl == null ? null : dl.get("client"));
        List<Download> tasks = new ArrayList<>();
        if (clientObj != null) {
            r.clientJar = verDir.resolve(versionId + ".jar");
            tasks.add(new Download(rewrite(Json.str(clientObj.get("url")), src), r.clientJar,
                    Json.str(clientObj.get("sha1")), (long) Json.num(clientObj.get("size"))));
        } else {
            r.clientJar = null; // 极老版本可能没有 client jar 条目
        }

        // 4) libraries
        Path libRoot = gameDir.resolve("libraries");
        r.nativesDir = verDir.resolve("natives");
        List<String> cp = new ArrayList<>();
        java.util.Set<String> cpSet = new java.util.HashSet<>();
        int libCount = 0;
        Map<String, String> features = noFeatures();

        for (Object o : Json.arrOrEmpty(vj.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            if (!rulesAllow(Json.arrOrNull(lib.get("rules")), features)) continue;

            String name = Json.str(lib.get("name")); // group:artifact:version[:classifier]

            Map<String, Object> libDl = Json.objOrNull(lib.get("downloads"));
            Map<String, Object> art = libDl == null ? null : Json.objOrNull(libDl.get("artifact"));
            Map<String, Object> nativesMap = Json.objOrNull(lib.get("natives"));
            boolean legacyNative = nativesMap != null;
            boolean modernNative = name != null && name.contains(":natives-");

            if (art != null && Json.str(art.get("url")) != null) {
                Path dst = libRoot.resolve(Json.str(art.get("path")));
                Download d = new Download(rewrite(Json.str(art.get("url")), src), dst,
                        Json.str(art.get("sha1")), (long) Json.num(art.get("size")));
                // 1.19+ 的 natives 包本身就要在 classpath 上，由 LWJGL 运行时自己解压
                if (cpSet.add(dst.toString())) cp.add(dst.toString());
                if (modernNative) d.extractTo = r.nativesDir;
                tasks.add(d);
                libCount++;
            }

            // 1.18 及以前: downloads.classifiers + natives 映射，解压到 natives 目录
            if (legacyNative && libDl != null) {
                String cls = Json.str(nativesMap.get(OS_NAME));
                if (cls == null) cls = Json.str(nativesMap.get("windows"));
                if (cls != null && libDl != null) {
                    Map<String, Object> clsMap = Json.objOrNull(libDl.get("classifiers"));
                    Map<String, Object> c = clsMap == null ? null : Json.objOrNull(clsMap.get(cls));
                    if (c != null) {
                        Path dst = libRoot.resolve(Json.str(c.get("path")));
                        Download d = new Download(rewrite(Json.str(c.get("url")), src), dst,
                                Json.str(c.get("sha1")), (long) Json.num(c.get("size")));
                        d.extractTo = r.nativesDir;
                        tasks.add(d);
                        libCount++;
                    }
                }
            }
        }

        // 5) assets index
        Map<String, Object> ai = Json.objOrNull(vj.get("assetIndex"));
        if (ai != null) {
            r.assetIndexId = Json.str(ai.get("id"));
            r.assetIndex = rewrite(Json.str(ai.get("url")), src);
            if (r.assetIndexId != null) {
                Path idx = gameDir.resolve("assets").resolve("indexes").resolve(r.assetIndexId + ".json");
                tasks.add(new Download(r.assetIndex, idx, Json.str(ai.get("sha1")),
                        (long) Json.num(ai.get("size"))));
            }
        }

        p.on("plan", 0, 0, String.format("%s: client=%s, libraries=%d, java=%d",
                versionId, r.clientJar != null ? "yes" : "no", libCount, r.requiredJava));

        if (planOnly) {
            long bytes = 0;
            for (Download d : tasks) bytes += Math.max(0, d.size);
            p.on("plan", 0, 0, String.format("待下载 %d 个文件, 约 %.1f MB",
                    tasks.size(), bytes / 1048576.0));
            r.classpath.addAll(cp.stream().map(Path::of).toList());
            if (r.clientJar != null) r.classpath.add(r.clientJar);
            buildArgs(r, "Player", "00000000-0000-0000-0000-000000000000", "0", gameDir);
            return r;
        }

        // 6) 下载 libraries + client
        Path tmp = gameDir.resolve(".mcver-tmp");
        Files.createDirectories(tmp);
        Files.createDirectories(r.nativesDir);
        downloadAll(http, tasks, jobs, p, "files", tmp);

        // 7) 解压 natives
        for (Download d : tasks) {
            if (d.extractTo != null && Files.exists(d.path)) extractZip(d.path, d.extractTo);
        }

        // 8) assets objects
        if (withAssets && r.assetIndexId != null) {
            downloadAssets(http, gameDir, r, src, jobs, p, tmp);
        }

        // 9) classpath
        r.classpath.addAll(cp.stream().map(Path::of).toList());
        if (r.clientJar != null) r.classpath.add(r.clientJar);

        buildArgs(r, "Player", "00000000-0000-0000-0000-000000000000", "0", gameDir);
        Launcher.markComplete(verDir);
        p.on("done", 0, 0, versionId + " 安装完成 -> " + verDir);
        return r;
    }

    // ---------------------------------------------------------------- 启动参数

    static String quote(String s) {
        if (s == null || s.isEmpty()) return "\"\"";
        if (s.indexOf(' ') < 0 && s.indexOf('"') < 0) return s;
        return '"' + s.replace("\"", "\\\"") + '"';
    }

    public static void buildArgs(Installed r, String player, String uuid, String token, Path gameDir) {
        Map<String, Object> vj = r.versionJson;
        Path assetsRoot = gameDir.resolve("assets");
        Map<String, String> ph = new HashMap<>();
        ph.put("auth_player_name", player);
        ph.put("version_name", r.versionId);
        ph.put("game_directory", gameDir.toString());
        ph.put("assets_root", assetsRoot.toString());
        ph.put("game_assets", assetsRoot.resolve("virtual").resolve(
                r.assetIndexId == null ? "legacy" : r.assetIndexId).toString());
        ph.put("assets_index_name", r.assetIndexId == null ? "legacy" : r.assetIndexId);
        ph.put("auth_uuid", uuid);
        ph.put("auth_access_token", token);
        ph.put("user_type", "legacy");
        ph.put("version_type", r.versionType);
        ph.put("natives_directory", r.nativesDir.toString());
        ph.put("launcher_name", "mcver");
        ph.put("launcher_version", "1.0");
        ph.put("classpath", joinCp(r.classpath));
        ph.put("classpath_separator", java.io.File.pathSeparator);
        ph.put("library_directory", gameDir.resolve("libraries").toString());
        ph.put("auth_xuid", "0");
        ph.put("clientid", "0");
        ph.put("user_properties", "{}");

        r.jvmArgs.clear();
        r.gameArgs.clear();

        Map<String, Object> args = Json.objOrNull(vj.get("arguments"));
        if (args != null) {
            for (Object o : Json.arrOrEmpty(args.get("jvm"))) {
                if (o instanceof String s) {
                    r.jvmArgs.add(sub(s, ph));
                } else if (o instanceof Map<?, ?> m) {
                    Map<String, Object> rule = Json.obj(m);
                    if (rulesAllow(Json.arrOrNull(rule.get("rules")), noFeatures()))
                        for (Object v : Json.arrOrEmpty(rule.get("value")))
                            r.jvmArgs.add(sub(Json.str(v), ph));
                }
            }
            for (Object o : Json.arrOrEmpty(args.get("game"))) {
                if (o instanceof String s) {
                    r.gameArgs.add(sub(s, ph));
                } else if (o instanceof Map<?, ?> m) {
                    Map<String, Object> rule = Json.obj(m);
                    if (rulesAllow(Json.arrOrNull(rule.get("rules")), noFeatures()))
                        for (Object v : Json.arrOrEmpty(rule.get("value")))
                            r.gameArgs.add(sub(Json.str(v), ph));
                }
            }
        } else if (vj.get("minecraftArguments") != null) {
            // 1.12 及以前
            r.jvmArgs.add("-Djava.library.path=" + r.nativesDir);
            r.jvmArgs.add("-cp");
            r.jvmArgs.add(joinCp(r.classpath));
            for (String s : Json.str(vj.get("minecraftArguments")).split(" "))
                if (!s.isBlank()) r.gameArgs.add(sub(s, ph));
        }

        if (r.mainClass == null) r.mainClass = "net.minecraft.client.main.Main";
    }

    static String joinCp(List<Path> cp) {
        StringBuilder sb = new StringBuilder();
        for (Path p : cp) {
            if (sb.length() > 0) sb.append(java.io.File.pathSeparatorChar);
            sb.append(p);
        }
        return sb.toString();
    }

    static final java.util.regex.Pattern PH = java.util.regex.Pattern.compile("\\$\\{([^}]+)}");

    static String sub(String s, Map<String, String> ph) {
        var m = PH.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = ph.get(m.group(1));
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static Map<String, String> noFeatures() {
        Map<String, String> f = new HashMap<>();
        f.put("is_demo_user", "false");
        f.put("has_custom_resolution", "false");
        f.put("has_quick_plays_support", "false");
        f.put("is_quick_play_singleplayer", "false");
        f.put("is_quick_play_multiplayer", "false");
        f.put("is_quick_play_realms", "false");
        return f;
    }

    /** 规则求值：无 rules 即允许；否则默认拒绝，按顺序命中定音。 */
    static boolean rulesAllow(List<Object> rules, Map<String, String> features) {
        if (rules == null) return true;
        boolean allow = false;
        for (Object o : rules) {
            Map<String, Object> rule = Json.obj(o);
            Map<String, Object> os = Json.objOrNull(rule.get("os"));
            if (os != null) {
                String n = Json.str(os.get("name"));
                if (n != null && !n.equals(OS_NAME)) continue;
                // 架构也要看：1.21.4 的 -Xss1M 那条规则是 os.arch=x86（只有 32 位才该加），
                // 以前忽略 arch 会导致 64 位也被塞 -Xss1M，线程栈只有 1MB。
                String arch = Json.str(os.get("arch"));
                if (arch != null && !archMatches(arch)) continue;
            }
            Map<String, Object> rf = Json.objOrNull(rule.get("features"));
            boolean featOk = true;
            if (rf != null) {
                for (String k : rf.keySet()) {
                    String want = String.valueOf(rf.get(k));
                    String have = features.getOrDefault(k, "false");
                    if (!want.equals(have)) { featOk = false; break; }
                }
            }
            if (!featOk) continue;
            allow = "allow".equals(Json.str(rule.get("action")));
        }
        return allow;
    }

    // ---------------------------------------------------------------- 下载

    static final class Download {
        final String url;
        final Path path;
        final String sha1;
        final long size;
        Path extractTo;

        Download(String url, Path path, String sha1, long size) {
            this.url = url; this.path = path; this.sha1 = sha1; this.size = size;
        }
    }

    static void downloadAll(HttpClient http, List<Download> tasks, int jobs,
                            Progress p, String stage, Path tmp) throws Exception {
        long totalBytes = 0;
        for (Download d : tasks) totalBytes += Math.max(0, d.size);
        final long total = totalBytes;

        List<Download> pending = new ArrayList<>(tasks);
        for (int round = 0; round < 4 && !pending.isEmpty(); round++) {
            if (round > 0) {
                p.on(stage, 0, tasks.size(), "第 " + round + " 轮重试 " + pending.size() + " 个文件");
                Thread.sleep(500L * round);
            }

            AtomicLong doneBytes = new AtomicLong();
            for (Download d : tasks) {
                if (!pending.contains(d)) doneBytes.addAndGet(Math.max(0, d.size));
            }
            AtomicLong counter = new AtomicLong();
            List<Download> failed = java.util.Collections.synchronizedList(new ArrayList<>());
            final List<Download> batch = new ArrayList<>(pending);

            ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, jobs));
            List<Future<?>> fs = new ArrayList<>();
            for (Download d : batch) {
                fs.add(pool.submit(() -> {
                    try {
                        if (needsDownload(d)) {
                            fetchTo(http, d.url, d.path, d.sha1, tmp);
                        }
                        doneBytes.addAndGet(Math.max(0, d.size));
                        long n = counter.incrementAndGet();
                        if (n % 20 == 0 || n == batch.size())
                            p.on(stage, n, tasks.size(),
                                    String.format("%.0f MB", doneBytes.get() / 1048576.0));
                    } catch (Exception e) {
                        failed.add(d);
                        System.err.println("下载失败 " + d.url + " : " + e);
                    }
                }));
            }
            for (Future<?> f : fs) f.get();
            pool.shutdown();
            pending = new ArrayList<>(failed);
        }

        // 收尾校验：sha1 不对或文件缺失的一律算失败，不当作成功
        List<Download> still = new ArrayList<>();
        for (Download d : tasks) if (needsDownload(d)) still.add(d);
        if (!still.isEmpty()) {
            throw new java.io.IOException("仍有 " + still.size() + " / " + tasks.size()
                    + " 个文件没下载成功，例如 " + still.get(0).url);
        }
        p.on(stage, tasks.size(), tasks.size(), String.format("%.0f MB", total / 1048576.0));
    }

    static void downloadAssets(HttpClient http, Path gameDir, Installed r, Sources.Src src,
                               int jobs, Progress p, Path tmp) throws Exception {
        Path idx = gameDir.resolve("assets").resolve("indexes").resolve(r.assetIndexId + ".json");
        if (!Files.exists(idx)) return;
        Map<String, Object> index = Json.obj(Json.parse(Files.readString(idx)));
        Map<String, Object> objects = Json.objOrNull(index.get("objects"));
        if (objects == null) return;

        boolean virtual = Boolean.TRUE.equals(index.get("virtual"));
        boolean mapToRes = Boolean.TRUE.equals(index.get("map_to_resources"));
        r.legacyAssets = virtual || mapToRes;

        List<Download> tasks = new ArrayList<>();
        List<String[]> legacy = new ArrayList<>();
        for (Map.Entry<String, Object> e : objects.entrySet()) {
            Map<String, Object> o = Json.obj(e.getValue());
            String hash = Json.str(o.get("hash"));
            long size = (long) Json.num(o.get("size"));
            Path dst = gameDir.resolve("assets").resolve("objects")
                    .resolve(hash.substring(0, 2)).resolve(hash);
            String url = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
            tasks.add(new Download(rewrite(url, src), dst, hash, size));
            if (virtual || mapToRes) legacy.add(new String[]{hash, e.getKey()});
        }
        p.on("assets", 0, tasks.size(), "共 " + tasks.size() + " 个对象");
        downloadAll(http, tasks, jobs, p, "assets", tmp);

        if (virtual) copyLegacy(gameDir, r.assetIndexId, legacy,
                gameDir.resolve("assets").resolve("virtual").resolve(r.assetIndexId));
        if (mapToRes) copyLegacy(gameDir, r.assetIndexId, legacy, gameDir.resolve("resources"));
    }

    static void copyLegacy(Path gameDir, String idxId, List<String[]> legacy, Path target)
            throws IOException {
        for (String[] l : legacy) {
            Path srcP = gameDir.resolve("assets").resolve("objects")
                    .resolve(l[0].substring(0, 2)).resolve(l[0]);
            Path dst = target.resolve(l[1]);
            if (Files.exists(srcP)) {
                Files.createDirectories(dst.getParent());
                Files.copy(srcP, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static boolean needsDownload(Download d) {
        try {
            if (!Files.exists(d.path)) return true;
            if (d.size > 0 && Files.size(d.path) != d.size) return true;
            if (d.sha1 != null && !d.sha1.isEmpty() && !sha1(d.path).equalsIgnoreCase(d.sha1)) return true;
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    static void fetchTo(HttpClient http, String url, Path dst, String sha1, Path tmp)
            throws Exception {
        Files.createDirectories(dst.getParent());
        Path part = Files.createTempFile(tmp, "dl", ".part");
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(5)).GET().build();
                HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                if (resp.statusCode() / 100 != 2)
                    throw new IOException("HTTP " + resp.statusCode() + " " + url);
                try (InputStream in = resp.body(); OutputStream out = Files.newOutputStream(part)) {
                    in.transferTo(out);
                }
                if (sha1 != null && !sha1.isEmpty() && !sha1(part).equalsIgnoreCase(sha1))
                    throw new IOException("sha1 不匹配 " + url);
                Files.move(part, dst, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) {
                last = e;
                try { Thread.sleep(500L * (attempt + 1)); } catch (InterruptedException ignored) { }
            }
        }
        Files.deleteIfExists(part);
        throw last == null ? new IOException(url) : last;
    }

    static String getString(HttpClient http, String url) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60)).GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() / 100 != 2) throw new IOException("HTTP " + resp.statusCode());
                return resp.body();
            } catch (Exception e) {
                last = e;
                Thread.sleep(600L * (attempt + 1));
            }
        }
        throw last;
    }

    static String sha1(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static void extractZip(Path zip, Path dir) throws IOException {
        Files.createDirectories(dir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory()) continue;
                if (name.startsWith("META-INF/") || name.endsWith(".sha1") || name.endsWith(".git"))
                    continue;
                Path out = dir.resolve(name).normalize();
                if (!out.startsWith(dir)) continue; // zip slip
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ---------------------------------------------------------------- 镜像

    public static String rewrite(String url, Sources.Src src) {
        if (url == null || src == null) return url;
        return src.rewrite(url);
    }

    static String detectOs() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "osx";
        return "linux";
    }

    // ---------------------------------------------------------------- 迷你 JSON

    static final class Json {
        private final String s;
        private int i;

        private Json(String s) { this.s = s; }

        static Object parse(String s) {
            // 记事本 / 别的启动器写出来的 json 常带 BOM；不剥掉的话 value() 会走到
            // default -> number()，空 token 直接抛 NumberFormatException（而且信息只有一句
            // 'For input string: ""'，完全看不出是 BOM 的锅）。
            if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1);
            Json j = new Json(s);
            j.ws();
            return j.value();
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> obj(Object o) { return (Map<String, Object>) o; }

        static Map<String, Object> objOrNull(Object o) { return o instanceof Map ? obj(o) : null; }

        @SuppressWarnings("unchecked")
        static List<Object> arr(Object o) { return (List<Object>) o; }

        static List<Object> arrOrNull(Object o) { return o instanceof List ? arr(o) : null; }

        static List<Object> arrOrEmpty(Object o) { return o instanceof List ? arr(o) : List.of(); }

        static String str(Object o) { return o == null ? null : String.valueOf(o); }

        static double num(Object o) {
            if (o == null) return 0;
            if (o instanceof Number n) return n.doubleValue();
            return Double.parseDouble(String.valueOf(o));
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            switch (c) {
                case '{': return object();
                case '[': return array();
                case '"': return string();
                case 't': i += 4; return Boolean.TRUE;
                case 'f': i += 5; return Boolean.FALSE;
                case 'n': i += 4; return null;
                default: return number();
            }
        }

        private Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws();
                String k = string();
                ws();
                i++; // :
                m.put(k, value());
                ws();
                char c = s.charAt(i++);
                if (c == '}') return m;
            }
        }

        private List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(value());
                ws();
                char c = s.charAt(i++);
                if (c == ']') return l;
            }
        }

        private String string() {
            StringBuilder sb = new StringBuilder();
            i++; // "
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }

        private Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.isEmpty()) return 0L;   // 兜底：别让一个坏字符炸掉整份 json
            if (t.contains(".") || t.contains("e") || t.contains("E")) {
                try { return Double.parseDouble(t); } catch (NumberFormatException e) { return 0D; }
            }
            try { return Long.parseLong(t); } catch (NumberFormatException e) { return 0L; }
        }
    }

    private Installer() { }
}
