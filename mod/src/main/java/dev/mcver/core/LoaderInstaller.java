package dev.mcver.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 加载器安装：下载官方 installer 跑 CLI。
 * OptiFine 没有 CLI，但它的 optifine.Installer.doInstall(File) 是 headless 的，
 * 用 URLClassLoader 反射直接调（HMCL / PCL 也是这么绕的）。
 */
public final class LoaderInstaller {

    /** 模组加载器。 */
    public enum Kind {
        NONE("原版"), FABRIC("Fabric"), QUILT("Quilt"), FORGE("Forge"), NEOFORGE("NeoForge");
        public final String label;
        Kind(String label) { this.label = label; }
    }

    /** 光影加载器。Iris 是 Fabric 的 mod；OptiFine 自带一套。 */
    public enum Shader {
        NONE("无"), IRIS("Iris"), OPTIFINE("OptiFine");
        public final String label;
        Shader(String label) { this.label = label; }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    // ================================================================ 入口

    /** kindVersion 为 null 时取推荐/最新。返回新生成的版本 id。 */
    public static String install(Path gameDir, Kind kind, String kindVersion, String mcVersion,
                                 Path javaExe, Consumer<String> log) throws Exception {
        Files.createDirectories(gameDir);
        ensureLauncherProfiles(gameDir);
        if (kind == null || kind == Kind.NONE) return null;

        switch (kind) {
            case FABRIC: return installFabric(gameDir, mcVersion, kindVersion, javaExe, log);
            case QUILT: return installQuilt(gameDir, mcVersion, kindVersion, javaExe, log);
            case FORGE: return installForge(gameDir, mcVersion, kindVersion, javaExe, log);
            case NEOFORGE: return installNeoForge(gameDir, mcVersion, kindVersion, javaExe, log);
            default: return null;
        }
    }

    // ---------------------------------------------------------------- Fabric

    static String installFabric(Path gameDir, String mc, String want, Path java, Consumer<String> log)
            throws Exception {
        String loader = want;
        if (loader == null || loader.isBlank()) {
            log.accept("查询 Fabric loader 版本…");
            String meta = Net.getMirrored(
                    "https://meta.fabricmc.net/v2/versions/loader/" + mc,
                    "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/" + mc,
                    "Fabric 版本表");
            List<Object> list = Json.arr(Json.parse(meta));
            if (list.isEmpty()) throw new IOException("Fabric 不支持 " + mc);
            loader = Json.str(Json.obj(Json.obj(list.get(0)).get("loader")), "version", "");
        }
        log.accept("Fabric loader " + loader + " / MC " + mc);

        String id = "fabric-loader-" + loader + "-" + mc;
        // 首选：拿官方 profile json 自己铺库（不依赖 installer 联网，快且稳）
        try {
            String profile = Net.getMirrored(
                    "https://meta.fabricmc.net/v2/versions/loader/" + mc + "/" + loader + "/profile/json",
                    "https://bmclapi2.bangbang93.com/fabric-meta/v2/versions/loader/" + mc + "/" + loader
                            + "/profile/json",
                    "Fabric profile");
            writeProfile(gameDir, id, profile, log);
            return id;
        } catch (Exception e) {
            log.accept("profile 方式失败（" + e.getMessage() + "），改用 installer");
        }

        Path jar = gameDir.resolve(".mcver-tmp").resolve("fabric-installer.jar");
        Files.createDirectories(jar.getParent());
        if (!Files.exists(jar)) {
            download("https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.0.1/fabric-installer-1.0.1.jar", jar);
        }
        run(java, log, "-jar", jar.toString(), "client",
                "-dir", gameDir.toAbsolutePath().toString(),
                "-mcversion", mc,
                "-loader", loader,
                "-noprofile");
        return id;
    }

    /**
     * 把 loader 的 profile json 落到 versions/&lt;id&gt;/&lt;id&gt;.json，并把里面的库全下下来。
     * 这是 PCL / HMCL 在网络不好时用的备用路线，我们直接把它当主路线。
     */
    static void writeProfile(Path gameDir, String id, String profileJson, Consumer<String> log) throws Exception {
        Map<String, Object> profile = Json.obj(Json.parse(profileJson));
        profile.put("id", id);

        List<Object> libs = Json.arr(profile.get("libraries"));
        log.accept("profile 里有 " + libs.size() + " 个库");

        Path libRoot = gameDir.resolve("libraries");
        int done = 0, skipped = 0;
        for (Object o : libs) {
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib, "name", "");
            if (name.isEmpty()) continue;
            String path = mavenPath(name);
            if (path == null) continue;

            Path dst = libRoot.resolve(path);
            if (Files.isRegularFile(dst) && Files.size(dst) > 512 && Net.isJar(dst)) { skipped++; continue; }
            Files.deleteIfExists(dst);   // 有可能是上次断掉留下的半成品

            String base = Json.str(lib, "url", "https://maven.fabricmc.net/");
            if (!base.endsWith("/")) base = base + "/";
            List<String> candidates = new ArrayList<>();
            // 镜像优先：maven.fabricmc.net 的 Cloudflare 从这边经常读超时
            boolean officialFirst = "official".equals(Config.downloadSource);
            if (officialFirst) candidates.add(base + path);
            candidates.add("https://bmclapi2.bangbang93.com/maven/" + path);
            candidates.add("https://maven.aliyun.com/repository/public/" + path);
            candidates.add(base + path);

            Exception last = null;
            for (String u : candidates) {
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        Net.download(u, dst, 8_000, 25_000);
                        last = null;
                        break;
                    } catch (Exception e) {
                        last = e;
                    }
                }
                if (last == null) break;
            }
            if (last != null) throw new IOException("下载库失败 " + name + ": " + last.getMessage());
            done++;
        }
        log.accept("库下载完成：新下 " + done + "，已有 " + skipped);

        Path verDir = gameDir.resolve("versions").resolve(id);
        Files.createDirectories(verDir);
        Launcher.markIncomplete(verDir);
        Files.writeString(verDir.resolve(id + ".json"), Json.write(profile), java.nio.charset.StandardCharsets.UTF_8);
        Launcher.markComplete(verDir);
        log.accept("写入 " + id + ".json");
    }

    /** group:artifact:version[:classifier] → libraries 下的相对路径。 */
    static String mavenPath(String name) {
        String[] p = name.split(":");
        if (p.length < 3) return null;
        String group = p[0].replace('.', '/');
        String artifact = p[1];
        String version = p[2];
        String classifier = p.length > 3 ? "-" + p[3] : "";
        String file = artifact + "-" + version + classifier + ".jar";
        String ext = p.length > 4 ? p[4] : "jar";
        if (!"jar".equals(ext)) file = artifact + "-" + version + classifier + "." + ext;
        return group + "/" + artifact + "/" + version + "/" + file;
    }

    // ---------------------------------------------------------------- Quilt

    static String installQuilt(Path gameDir, String mc, String want, Path java, Consumer<String> log)
            throws Exception {
        String loader = want;
        if (loader == null || loader.isBlank()) {
            String meta = get("https://meta.quiltmc.org/v3/versions/loader/" + mc);
            List<Object> list = Json.arr(Json.parse(meta));
            if (list.isEmpty()) throw new IOException("Quilt 不支持 " + mc);
            loader = Json.str(Json.obj(Json.obj(list.get(0)).get("loader")), "version", "");
        }
        log.accept("Quilt loader " + loader + " / MC " + mc);

        String id = "quilt-loader-" + loader + "-" + mc;
        try {
            String profile = get("https://meta.quiltmc.org/v3/versions/loader/" + mc + "/" + loader + "/profile/json");
            writeProfile(gameDir, id, profile, log);
            return id;
        } catch (Exception e) {
            log.accept("profile 方式失败（" + e.getMessage() + "），改用 installer");
        }

        String installerVer = quiltInstallerVersion();
        Path jar = gameDir.resolve(".mcver-tmp").resolve("quilt-installer-" + installerVer + ".jar");
        Files.createDirectories(jar.getParent());
        if (!Files.exists(jar)) {
            download("https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-installer/"
                    + installerVer + "/quilt-installer-" + installerVer + ".jar", jar);
        }
        run(java, log, "-jar", jar.toString(), "install", "client", mc,
                "--loader=" + loader,
                "--install-dir=" + gameDir.toAbsolutePath());
        return id;
    }

    static String quiltInstallerVersion() {
        try {
            String meta = get("https://meta.quiltmc.org/v3/versions/installer");
            List<Object> list = Json.arr(Json.parse(meta));
            if (!list.isEmpty()) {
                String v = Json.str(Json.obj(list.get(0)), "version", "");
                if (!v.isEmpty()) return v;
            }
        } catch (Exception ignored) { }
        return "0.9.2";
    }

    // ---------------------------------------------------------------- Forge

    static String installForge(Path gameDir, String mc, String want, Path java, Consumer<String> log)
            throws Exception {
        String ver = want;
        if (ver == null || ver.isBlank()) {
            log.accept("查询 Forge 版本…");
            String promosRaw = get("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");
            Map<String, Object> promos = Json.obj(Json.obj(Json.parse(promosRaw)).get("promos"));
            ver = Json.str(promos, mc + "-recommended", null);
            if (ver == null) ver = Json.str(promos, mc + "-latest", null);
            if (ver == null) throw new IOException("Forge 没有 " + mc + " 的版本");
        }
        String full = mc + "-" + ver;
        log.accept("Forge " + full);

        Path jar = gameDir.resolve(".mcver-tmp").resolve("forge-" + full + "-installer.jar");
        Files.createDirectories(jar.getParent());
        String rel = "net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar";
        downloadFirst(new String[]{
                Sources.active().rewrite("https://maven.minecraftforge.net/" + rel),
                "https://bmclapi2.bangbang93.com/maven/" + rel,
                "https://maven.minecraftforge.net/" + rel
        }, jar);

        run(java, log, "-jar", jar.toString(),
                "--installClient", gameDir.toAbsolutePath().toString());
        // 版本 id 不要硬猜：1.20.2 是 forge-<mc>-<ver>，1.20.3 起变成 <mc>-forge<ver>
        String found = findFreshVersionId(gameDir, full, ver);
        return found != null ? found : "forge-" + full;
    }

    /**
     * 装完之后去 versions/ 里找刚生成的版本目录（名字里带任一 hint，多个命中取最新的）。
     * Forge/NeoForge 不同版本命名不一致，硬拼字符串会踩坑。
     */
    static String findFreshVersionId(Path gameDir, String... hints) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        try (var ds = Files.list(dir)) {
            return ds.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        for (String h : hints) {
                            if (h != null && !h.isBlank() && n.contains(h)) return true;
                        }
                        return false;
                    })
                    .max(java.util.Comparator.comparingLong(d -> d.toFile().lastModified()))
                    .map(d -> d.getFileName().toString())
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- NeoForge

    static String installNeoForge(Path gameDir, String mc, String want, Path java, Consumer<String> log)
            throws Exception {
        String best = want;
        if (best == null || best.isBlank()) {
            // 以前直接查 maven.neoforged.net 的 API —— 国内连不上（DNS 污染/链路不通），
            // 走 LoaderVersions 那套「BMCLAPI 优先、官方兵底」的多源列表。
            log.accept("查询 NeoForge 版本…");
            for (LoaderVersions.Lv lv : LoaderVersions.fetch(Kind.NEOFORGE, mc)) {
                if (best == null || compareVersions(lv.id(), best) > 0) best = lv.id();
            }
            if (best == null) throw new IOException("NeoForge 没有 " + mc + " 的版本");
        }
        log.accept("NeoForge " + best);

        // 1.20.1 时代 NeoForge 发在 net.neoforged:forge（文件名 forge-<mc>-<ver>-installer.jar），
        // 1.20.2+ 才改成 net.neoforged:neoforge。两套路径都试。
        String bare = best.startsWith(mc + "-") ? best.substring(mc.length() + 1) : best;
        Path jar = gameDir.resolve(".mcver-tmp").resolve("neoforge-" + bare + "-installer.jar");
        Files.createDirectories(jar.getParent());
        String relNeo = "net/neoforged/neoforge/" + bare + "/neoforge-" + bare + "-installer.jar";
        String relForge = "net/neoforged/forge/" + mc + "-" + bare + "/forge-" + mc + "-" + bare + "-installer.jar";
        downloadFirst(new String[]{
                "https://bmclapi2.bangbang93.com/maven/" + relNeo,
                "https://bmclapi2.bangbang93.com/maven/" + relForge,
                Sources.active().rewrite("https://maven.neoforged.net/releases/" + relNeo),
                "https://maven.neoforged.net/releases/" + relForge
        }, jar);

        try {
            run(java, log, "-jar", jar.toString(),
                    "--installClient", gameDir.toAbsolutePath().toString());
            String found = findFreshVersionId(gameDir, bare);
            return found != null ? found : "neoforge-" + bare;
        } catch (IOException e) {
            // NeoForge 20.2+ 的安装器把客户端选项砍了（只有 --installServer/--extract/--mirror/--offline），
            // 直接跑 --installClient 会 UnrecognizedOptionException。改走 install_profile 自装。
            log.accept("安装器不支持 --installClient（" + e.getMessage() + "），改用内置处理器流程");
            try {
                return installViaInstallProfile(gameDir, jar, java, log);
            } catch (Exception e2) {
                throw new IOException("内置处理器流程也失败了：" + e2.getMessage()
                        + "。若提示下载 404 / 超时，多半是 maven.neoforged.net 在当前网络不可达"
                        + "（NeoForge 的库只发在这个域名上，BMCLAPI 镜像不全）。", e2);
            }
        }
    }

    // ---------------------------------------------------------------- install_profile 自装（NeoForge 20.2+）

    /**
     * 按 Forge spec=1 的 install_profile 自己跑一遍「客户端」安装。
     *
     * <p>安装器 jar 里带：
     * <ul>
     *   <li>{@code version.json} —— 要写进 versions/&lt;id&gt;/&lt;id&gt;.json 的版本描述</li>
     *   <li>{@code install_profile.json} —— data 表 + processors 列表 + 处理器依赖</li>
     *   <li>{@code data/client.lzma} 等 —— 二进制补丁，安装时解出来给 binarypatcher 用</li>
     * </ul>
     *
     * <p>客户端要跑的处理器（20.3.x）：MCP_DATA → DOWNLOAD_MOJMAPS → MERGE_MAPPING
     * → jarsplitter(client) → ForgeAutoRenamingTool → binarypatcher。
     */
    static String installViaInstallProfile(Path gameDir, Path installerJar, Path java,
                                          Consumer<String> log) throws Exception {
        Map<String, Object> profile;
        String versionJson;
        String profileJson;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(installerJar.toFile())) {
            versionJson = zipText(z, "version.json");
            profileJson = zipText(z, "install_profile.json");
        }
        if (profileJson == null || versionJson == null) {
            throw new IOException("安装器里没有 install_profile.json / version.json");
        }
        profile = Json.obj(Json.parse(profileJson));
        if (profile == null) throw new IOException("install_profile.json 不是对象");
        Map<String, Object> vj = Json.obj(Json.parse(versionJson));
        String id = Json.str(vj, "id", null);
        if (id == null || id.isBlank()) throw new IOException("version.json 里没有 id");
        String mc = Json.str(profile, "minecraft", Json.str(vj, "inheritsFrom", null));
        Path mcJar = gameDir.resolve("versions").resolve(mc).resolve(mc + ".jar");
        if (!Files.isRegularFile(mcJar)) throw new IOException("找不到原版 jar: " + mcJar);

        Path verDir = gameDir.resolve("versions").resolve(id);
        Files.createDirectories(verDir);
        Files.write(verDir.resolve(id + ".json"), versionJson.getBytes(StandardCharsets.UTF_8));
        log.accept("写出版本 json: versions/" + id + "/" + id + ".json");

        // 1) 运行库：**并行**下载。串行的话 180 个库在国内网络能拖半小时（每个库还试多个镜像）。
        java.util.LinkedHashSet<String> coords = new java.util.LinkedHashSet<>();        for (Object o : Json.arr(vj.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib, "name", null);
            if (name != null && !name.isBlank()) coords.add(name);
        }
        for (Object o : Json.arr(profile.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib, "name", null);
            if (name != null && !name.isBlank()) coords.add(name);
        }
        int total = coords.size();
        int threads = Math.max(1, Math.min(8, Config.downloadThreads > 0 ? Config.downloadThreads : 4));
        List<Exception> errs = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (String coord : coords) {
            pool.submit(() -> {
                try {
                    ensureMaven(gameDir, coord);
                } catch (Exception e) {
                    errs.add(e);
                }
                int d = done.incrementAndGet();
                if (d % 20 == 0 || d == total) log.accept("运行库 " + d + "/" + total);
            });
        }
        pool.shutdown();
        if (!pool.awaitTermination(40, TimeUnit.MINUTES)) {
            throw new IOException("运行库下载超时");
        }
        if (!errs.isEmpty()) throw errs.get(0);
        log.accept("运行库就绪 " + total + " 个（并行 " + threads + "）");

        // 2) 固定占位符 + data 表
        Map<String, String> builtins = new java.util.LinkedHashMap<>();
        builtins.put("SIDE", "client");
        builtins.put("MINECRAFT_JAR", mcJar.toAbsolutePath().toString());
        builtins.put("MINECRAFT_VERSION", mc);
        builtins.put("ROOT", gameDir.toAbsolutePath().toString());
        builtins.put("INSTALLER", installerJar.toAbsolutePath().toString());
        builtins.put("LIBRARY_DIR", gameDir.resolve("libraries").toAbsolutePath().toString());

        Map<String, String> data = new java.util.LinkedHashMap<>();
        Map<String, Object> dataObj = Json.obj(profile.get("data"));
        if (dataObj != null) {
            for (Map.Entry<String, Object> e : dataObj.entrySet()) {
                Map<String, Object> sides = Json.obj(e.getValue());
                if (sides == null) continue;
                String raw = Json.str(sides, "client", null);
                if (raw == null) raw = Json.str(sides, "server", null);
                if (raw == null) continue;
                data.put(e.getKey(), resolveDataValue(gameDir, installerJar, raw.trim(), log));
            }
        }

        // 3) 跑处理器（sides 为空或包含 client）
        int idx = 0;
        for (Object o : Json.arr(profile.get("processors"))) {
            Map<String, Object> p = Json.obj(o);
            idx++;
            if (!includesClient(Json.arr(p.get("sides")))) {
                log.accept("跳过处理器 #" + idx + "（仅服务端）");
                continue;
            }
            String jarCoord = Json.str(p, "jar", null);
            if (jarCoord == null) continue;
            Path procJar = ensureMaven(gameDir, jarCoord);

            List<String> cp = new ArrayList<>();
            for (Object c : Json.arr(p.get("classpath"))) {
                String coord = Json.str(c);
                if (coord != null && !coord.isBlank()) cp.add(ensureMaven(gameDir, coord).toString());
            }
            if (cp.isEmpty()) cp.add(procJar.toString());
            // 处理器自己的 jar 必须进 classpath：有些 profile（如 NeoForge 1.20.1）的
            // classpath 列表只列依赖，不列工具本体 → ClassNotFoundException: ...ConsoleTool
            if (!cp.contains(procJar.toString())) cp.add(0, procJar.toString());

            String main = mainClassOf(procJar);
            if (main == null) throw new IOException("处理器 jar 没有 Main-Class: " + procJar.getFileName());

            List<String> argv = new ArrayList<>();
            argv.add("-cp");
            argv.add(String.join(File.pathSeparator, cp));
            argv.add(main);
            for (Object a : Json.arr(p.get("args"))) {
                String arg = Json.str(a);
                if (arg != null) argv.add(substitute(gameDir, arg, data, builtins));
            }
            log.accept("处理器 #" + idx + " " + procJar.getFileName());
            run(java, log, argv.toArray(new String[0]));
        }

        // 4) 自检：补丁后的客户端 jar 必须真的产出来了
        String patched = data.get("PATCHED");
        if (patched != null && !Files.isRegularFile(Paths.get(patched))) {
            throw new IOException("处理器跑完了但没生成客户端 jar: " + patched);
        }
        if (!Files.isRegularFile(verDir.resolve(id + ".json"))) {
            throw new IOException("版本 json 没写成: " + verDir);
        }
        log.accept("安装完成: " + id);
        return id;
    }

    /** 把 "[group:artifact:version:classifier@ext]" 之类解析成 libraries/ 下的真实文件，缺了就下。 */
    static Path ensureMaven(Path gameDir, String coordRaw) throws Exception {
        Mvn m = Mvn.parse(coordRaw);
        Path dst = m.file(gameDir);
        if (Files.isRegularFile(dst) && Files.size(dst) > 0) return dst;
        Files.createDirectories(dst.getParent());
        try {
            downloadFirst(mavenUrls(m).toArray(new String[0]), dst);
            return dst;
        } catch (Exception e) {
            // 有些版本在上游被撤回/压根没发成（例：NeoForge 20.3.x 引用的
            // net.neoforged.fancymodloader:events:1.0.16 在所有镜像都是 404）。
            // 补丁号相邻的版本一般二进制兼容：按 +1..+3 / -1..-3 找一个能下的，存到原定路径。
            for (String v : neighbourVersions(m.version)) {
                Mvn alt = new Mvn(m.group, m.artifact, v, m.classifier, m.ext);
                try {
                    downloadFirst(mavenUrls(alt).toArray(new String[0]), dst);
                    return dst;
                } catch (Exception ignored) { }
            }
            throw e;
        }
    }

    /** 同一个 artifact 的候选下载地址（当前下载源 → BMCLAPI → neoforged → forge → central）。 */
    private static List<String> mavenUrls(Mvn m) {
        String rel = m.rel();
        List<String> urls = new ArrayList<>();
        String srcRoot = Sources.active().rootClean();
        if (!srcRoot.isEmpty()) urls.add(srcRoot + "/maven/" + rel);
        urls.add("https://bmclapi2.bangbang93.com/maven/" + rel);
        urls.add("https://maven.minecraftforge.net/" + rel);
        urls.add("https://repo1.maven.org/maven2/" + rel);
        // 最后才试 neoforged：国内这条链路基本不通，放前面会把每个库都拖到超时
        urls.add("https://maven.neoforged.net/releases/" + rel);
        return urls;
    }

    /** 1.0.16 → [1.0.17, 1.0.18, 1.0.19, 1.0.15, 1.0.14, 1.0.13]；非三段版本号返回空。 */
    private static List<String> neighbourVersions(String version) {
        List<String> out = new ArrayList<>();
        String[] p = version.split("\\.");
        if (p.length != 3) return out;
        int major, minor, patch;
        try {
            major = Integer.parseInt(p[0]);
            minor = Integer.parseInt(p[1]);
            patch = Integer.parseInt(p[2]);
        } catch (NumberFormatException e) {
            return out;
        }
        for (int d = 1; d <= 3; d++) out.add(major + "." + minor + "." + (patch + d));
        for (int d = 1; d <= 3; d++) if (patch - d >= 0) out.add(major + "." + minor + "." + (patch - d));
        return out;
    }

    /** maven 坐标 → libraries 下的路径。 */
    private static final class Mvn {
        final String group, artifact, version, classifier, ext;

        Mvn(String group, String artifact, String version, String classifier, String ext) {
            this.group = group;
            this.artifact = artifact;
            this.version = version;
            this.classifier = classifier;
            this.ext = ext;
        }

        static Mvn parse(String coordRaw) throws IOException {
            String coord = coordRaw == null ? "" : coordRaw.trim();
            if (coord.startsWith("[") && coord.endsWith("]")) coord = coord.substring(1, coord.length() - 1).trim();
            String ext = "jar";
            int at = coord.indexOf('@');
            if (at >= 0) {
                ext = coord.substring(at + 1).trim();
                coord = coord.substring(0, at).trim();
            }
            String[] p = coord.split(":");
            if (p.length < 3) throw new IOException("无法解析 maven 坐标: " + coordRaw);
            String classifier = p.length > 3 && !p[3].isBlank() ? p[3].trim() : null;
            return new Mvn(p[0].trim(), p[1].trim(), p[2].trim(), classifier, ext);
        }

        String fileName() {
            return artifact + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext;
        }

        String rel() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + fileName();
        }

        Path file(Path gameDir) {
            return gameDir.resolve("libraries").resolve(group.replace('.', '/'))
                    .resolve(artifact).resolve(version).resolve(fileName());
        }
    }

    private static String zipText(java.util.zip.ZipFile z, String name) throws IOException {
        java.util.zip.ZipEntry e = z.getEntry(name);
        if (e == null) return null;
        try (var in = z.getInputStream(e)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean includesClient(List<Object> sides) {
        if (sides == null || sides.isEmpty()) return true;
        for (Object s : sides) {
            String v = Json.str(s);
            if (v != null && v.equalsIgnoreCase("client")) return true;
        }
        return false;
    }

    /** data 表的值：[maven] / /安装器内路径 / '字面量' */
    private static String resolveDataValue(Path gameDir, Path installerJar, String raw,
                                           Consumer<String> log) throws Exception {
        if (raw.startsWith("[") && raw.endsWith("]")) {
            // data 表里的 maven 坐标绝大多数是**产物路径**（MC_SLIM / MC_SRG / PATCHED …），
            // 由处理器写出来，不能去下载（下载必 404：client-…-slim.jar 根本没发布过）。
            return Mvn.parse(raw).file(gameDir).toString();
        }
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() > 1) {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.startsWith("/")) {
            // 安装器 jar 里的资源（如 /data/client.lzma）→ 解到 .mcver-tmp 下
            String entry = raw.substring(1);
            Path out = gameDir.resolve(".mcver-tmp").resolve("nf-data").resolve(entry);
            Files.createDirectories(out.getParent());
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(installerJar.toFile())) {
                java.util.zip.ZipEntry e = z.getEntry(entry);
                if (e == null) throw new IOException("安装器里找不到 " + raw);
                try (var in = z.getInputStream(e)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return out.toAbsolutePath().toString();
        }
        return raw;
    }

    /** 处理器参数里的 [maven] / {KEY} 替换。 */
    private static String substitute(Path gameDir, String arg,
                                     Map<String, String> data,
                                     Map<String, String> builtins) throws Exception {
        String a = arg == null ? "" : arg.trim();
        if (a.startsWith("[") && a.endsWith("]")) {
            return ensureMaven(gameDir, a.substring(1, a.length() - 1)).toString();
        }
        if (a.startsWith("{") && a.endsWith("}")) {
            String k = a.substring(1, a.length() - 1);
            String v = builtins.get(k);
            if (v == null) v = data.get(k);
            if (v != null) return v;
        }
        return a;
    }

    /** 从 jar 的 manifest 读 Main-Class。 */
    private static String mainClassOf(Path jar) throws IOException {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar.toFile())) {
            String mf = zipText(z, "META-INF/MANIFEST.MF");
            if (mf == null) return null;
            for (String line : mf.split("\r?\n")) {
                if (line.startsWith("Main-Class:")) return line.substring("Main-Class:".length()).trim();
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- OptiFine

    /**
     * OptiFine 的安装器是 GUI 程序，但 optifine.Installer.doInstall(File) 静态方法能直接调，
     * 全程不弹窗。ofId 形如 "HD_U/I6"。
     */
    public static String installOptifine(Path gameDir, String mc, String ofId,
                                         Consumer<String> log) throws Exception {
        ensureLauncherProfiles(gameDir);

        String safe = ofId.replace('/', '_');
        Path jar = gameDir.resolve(".mcver-tmp").resolve("OptiFine_" + mc + "_" + safe + ".jar");
        Files.createDirectories(jar.getParent());
        log.accept("下载 OptiFine " + mc + " " + ofId);
        download("https://bmclapi2.bangbang93.com/optifine/" + mc + "/" + ofId, jar);

        log.accept("静默运行 OptiFine 安装器…");
        invokeOptifineInstaller(jar, gameDir, log);

        // 找出刚生成/更新的版本目录
        String id = newestOptifineVersion(gameDir, mc);
        if (id == null) throw new IOException("OptiFine 安装器没有生成版本目录");
        log.accept("OptiFine 版本: " + id);
        return id;
    }

    private static void invokeOptifineInstaller(Path jar, Path gameDir, Consumer<String> log) throws Exception {
        // 安装器会往 stdout 打一堆 "Same:" 之类的噪音，这里只保留关键行
        URLClassLoader cl = new URLClassLoader(
                new URL[]{jar.toUri().toURL()},
                LoaderInstaller.class.getClassLoader());
        try {
            Class<?> c = cl.loadClass("optifine.Installer");
            Method m = c.getMethod("doInstall", java.io.File.class);
            m.invoke(null, gameDir.toAbsolutePath().toFile());
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IOException("OptiFine 安装器报错: " + cause.getMessage(), cause);
        } finally {
            try { cl.close(); } catch (IOException ignored) { }
        }
        log.accept("OptiFine 安装器执行完毕");
    }

    private static String newestOptifineVersion(Path gameDir, String mc) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        String best = null;
        long bestTime = -1;
        try (var ds = Files.list(dir)) {
            for (Path p : (Iterable<Path>) ds::iterator) {
                String n = p.getFileName().toString();
                if (!n.startsWith(mc + "-OptiFine")) continue;
                if (!Files.isRegularFile(p.resolve(n + ".json"))) continue;
                long t = Files.getLastModifiedTime(p.resolve(n + ".json")).toMillis();
                if (t > bestTime) { bestTime = t; best = n; }
            }
        } catch (IOException ignored) { }
        return best;
    }

    // ---------------------------------------------------------------- Iris / Sodium

    /** Iris 是 Fabric 的 mod，装进 mods 目录；顺带装 Sodium（不装会卡）。 */
    public static void installIris(Path modsDir, String mc, String wantIrisVer,
                                   Consumer<String> log) throws Exception {
        Files.createDirectories(modsDir);

        List<Modrinth.Ver> iris = Modrinth.versions("iris", mc, "fabric");
        if (iris.isEmpty()) throw new IOException("Iris 没有适配 " + mc + " 的版本");
        Modrinth.Ver pick = null;
        if (wantIrisVer != null && !wantIrisVer.isBlank()) {
            for (Modrinth.Ver v : iris) {
                if (v.number().equals(wantIrisVer)) { pick = v; break; }
            }
        }
        if (pick == null) pick = iris.get(0);

        log.accept("下载 Iris " + pick.number());
        Path f = Modrinth.download(pick.files().get(0), modsDir);
        log.accept("Iris -> " + f.getFileName());

        try {
            List<Modrinth.Ver> sodium = Modrinth.versions("sodium", mc, "fabric");
            if (!sodium.isEmpty()) {
                Modrinth.Ver s = sodium.get(0);
                Path sf = Modrinth.download(s.files().get(0), modsDir);
                log.accept("Sodium " + s.number() + " -> " + sf.getFileName());
            }
        } catch (Exception e) {
            log.accept("（Sodium 没装上，Iris 仍可用）" + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- 公共

    /** Forge/NeoForge/OptiFine 的 installer 都要求目录里有 launcher_profiles.json。 */
    public static void ensureLauncherProfiles(Path gameDir) throws IOException {
        Path p = gameDir.resolve("launcher_profiles.json");
        if (Files.exists(p)) return;
        String json = """
                {
                  "profiles": {},
                  "settings": { "enableSnapshots": false },
                  "version": 3
                }
                """;
        // 必须无 BOM：OptiFine 安装器的 JSON 解析器受不了 BOM
        Files.write(p, json.getBytes(StandardCharsets.UTF_8));
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-]"), y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? toInt(x[i]) : 0;
            int yi = i < y.length ? toInt(y[i]) : 0;
            if (xi != yi) return Integer.compare(xi, yi);
        }
        return a.compareTo(b);
    }

    static int toInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    /** 按顺序试多个地址，第一个成功就停。 */
    /**
     * 已知连不上的主机（国内环境常见：maven.neoforged.net）。
     * 一旦某个主机的失败看起来是「网络层」（超时/拒绝/解析不了），就记下来，
     * 后面的库不再在每个 artifact 上重新等一次超时——否则 180 个库 × 15 秒 = 45 分钟。
     */
    private static final java.util.Set<String> DEAD_HOSTS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean looksLikeNetworkFailure(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        return m.contains("timed out") || m.contains("timeout") || m.contains("connect")
                || m.contains("unknownhost") || m.contains("unknown host") || m.contains("refused")
                || m.contains("no route") || m.contains("network is unreachable");
    }

    static void downloadFirst(String[] urls, Path dst) throws Exception {
        Exception last = null;
        java.util.LinkedHashSet<String> tried = new java.util.LinkedHashSet<>();
        java.util.List<String> fresh = new java.util.ArrayList<>();
        for (String u : urls) {
            if (u == null || !tried.add(u)) continue;
            String h = hostOf(u);
            if (h != null && DEAD_HOSTS.contains(h)) continue;   // 已经确认死了，别浪费时间
            fresh.add(u);
        }
        // 全被跳过时至少保留原列表，让错误信息仍然可见
        if (fresh.isEmpty()) for (String u : urls) if (u != null) fresh.add(u);

        for (String u : fresh) {
            try {
                Net.download(u, dst, 15_000, 120_000);
                return;
            } catch (Exception e) {
                last = e;
                String h = hostOf(u);
                if (h != null && looksLikeNetworkFailure(e.getMessage())) {
                    if (DEAD_HOSTS.add(h)) {
                        // 只在第一次拉黑时提一句，别刷屏
                        System.err.println("[mcver] 跳过不可达主机: " + h);
                    }
                }
                try { java.nio.file.Files.deleteIfExists(dst); } catch (Exception ignored) { }
            }
        }
        throw last == null ? new IOException("没有可用的下载地址") : last;
    }

    static void download(String url, Path dst) throws Exception {
        Sources.Src src = Sources.active();
        String first = src.rewrite(url);
        if (!first.equals(url)) {
            try {
                Net.download(first, dst, 15_000, 120_000);
                return;
            } catch (Exception e) {
                Net.download(url, dst, 15_000, 120_000);
                return;
            }
        }
        Net.download(url, dst, 15_000, 120_000);
    }

    static String get(String url) throws Exception {
        Sources.Src src = Sources.active();
        String first = src.rewrite(url);
        if (!first.equals(url)) {
            try {
                return Net.get(first);
            } catch (Exception e) {
                return Net.get(url);
            }
        }
        return Net.get(url);
    }

    static void run(Path javaExe, Consumer<String> log, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe.toString());
        // Java 默认优先 IPv6，本机 IPv6 不通时安装器会卡到超时
        cmd.add("-Djava.net.preferIPv4Stack=true");
        for (String a : args) cmd.add(a);
        log.accept("$ " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) log.accept(line);
            }
        }
        int code = proc.waitFor();
        if (code != 0) throw new IOException("installer 退出码 " + code);
    }

    /** 从已安装目录里找出某 MC 版本对应的 loader 版本 id（用于提示）。 */
    public static String findInstalledLoaderId(Path gameDir, String mc) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        try (var ds = Files.list(dir)) {
            return ds.filter(Files::isDirectory)
                    .map(d -> d.getFileName().toString())
                    .filter(n -> (n.startsWith("fabric-loader-") || n.startsWith("quilt-loader-")) && n.endsWith(mc)
                            || n.startsWith("forge-" + mc)
                            || n.startsWith("neoforge-"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private LoaderInstaller() { }
}
