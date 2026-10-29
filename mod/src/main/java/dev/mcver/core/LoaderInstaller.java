package dev.mcver.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        if (loader == null || loader.trim().isEmpty()) {
            log.accept("查询 Fabric loader 版本…");
            String mbase = FabricMeta.metaBase(mc);
            String mmirror = FabricMeta.mirrorBase(mc);
            String meta = mmirror == null
                    ? Net.get(mbase + "v2/versions/loader/" + mc, 10_000, 30_000)
                    : Net.getMirrored(mbase + "v2/versions/loader/" + mc,
                            mmirror + "v2/versions/loader/" + mc, "Fabric 版本表");
            List<Object> list = Json.arr(Json.parse(meta));
            if (list.isEmpty()) throw new IOException("Fabric 不支持 " + mc);
            loader = Json.str(Json.obj(Json.obj(list.get(0)).get("loader")), "version", "");
        }
        log.accept("Fabric loader " + loader + " / MC " + mc);

        String id = "fabric-loader-" + loader + "-" + mc;
        // 首选：拿官方 profile json 自己铺库（不依赖 installer 联网，快且稳）
        try {
            String pbase = FabricMeta.metaBase(mc);
            String pmirror = FabricMeta.mirrorBase(mc);
            String profile = pmirror == null
                    ? Net.get(pbase + "v2/versions/loader/" + mc + "/" + loader + "/profile/json", 10_000, 30_000)
                    : Net.getMirrored(pbase + "v2/versions/loader/" + mc + "/" + loader + "/profile/json",
                            pmirror + "v2/versions/loader/" + mc + "/" + loader + "/profile/json",
                            "Fabric profile");
            writeProfile(gameDir, id, profile, log);
            return id;
        } catch (Exception e) {
            if (FabricMeta.isLegacy(mc)) {
                // 官方 fabric-installer 把 meta 写死成 meta.fabricmc.net，对 Legacy 只会回 400，白跑一次
                throw new IOException("用 Legacy Fabric 装 " + mc + " 失败：" + e.getMessage(), e);
            }
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
        Path nativesDir = gameDir.resolve("versions").resolve(id).resolve("natives");
        int done = 0, skipped = 0, nativeCount = 0;
        for (Object o : libs) {
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib, "name", "");
            if (name.isEmpty()) continue;
            String base = Json.str(lib, "url", "https://maven.fabricmc.net/");

            // 带 natives 映射的库（1.13 及以前的 LWJGL 2.x）：**只有 classifier 包，没有 plain jar**。
            // Legacy Fabric 的 profile 就是这种：lwjgl-platform-2.9.4+legacyfabric.17.jar 根本不存在，
            // 只有 -natives-windows.jar。不特殊处理就会 404 然后整个安装失败。
            Map<String, Object> nativesMap = Json.objOrNull(lib.get("natives"));
            if (nativesMap != null) {
                String cls = Json.str(nativesMap.get(osKey()));
                if (cls == null) cls = Json.str(nativesMap.get("windows"));
                if (cls != null) {
                    String np = mavenPath(name + ":" + cls);
                    if (np != null) {
                        Path nd = libRoot.resolve(np);
                        if (!isGoodJar(nd)) {
                            Files.deleteIfExists(nd);
                            fetchMaven(base, np, nd, name + ":" + cls);
                        }
                        Files.createDirectories(nativesDir);
                        Installer.extractZip(nd, nativesDir);
                        nativeCount++;
                        done++;
                    }
                }
                continue;   // natives 库不进 classpath，也不下 plain jar
            }

            String path = mavenPath(name);
            if (path == null) continue;
            Path dst = libRoot.resolve(path);
            if (isGoodJar(dst)) { skipped++; continue; }
            Files.deleteIfExists(dst);   // 有可能是上次断掉留下的半成品
            fetchMaven(base, path, dst, name);
            done++;
        }
        log.accept("库下载完成：新下 " + done + "（含 natives " + nativeCount + "），已有 " + skipped);

        Path verDir = gameDir.resolve("versions").resolve(id);
        Files.createDirectories(verDir);
        Launcher.markIncomplete(verDir);
        dev.mcver.core.Jdk.writeString(verDir.resolve(id + ".json"), Json.write(profile), java.nio.charset.StandardCharsets.UTF_8);
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

    /** 当前系统在 natives 映射里的 key。 */
    private static String osKey() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static boolean isGoodJar(Path p) {
        try {
            return Files.isRegularFile(p) && Files.size(p) > 512 && Net.isJar(p);
        } catch (Exception e) {
            return false;
        }
    }

    /** 按候选源依次尝试下一个 maven 文件（镜像优先，最后回官方 / Legacy maven）。 */
    private static void fetchMaven(String base, String path, Path dst, String label) throws Exception {
        if (!base.endsWith("/")) base = base + "/";
        List<String> candidates = new ArrayList<>();
        // 镜像优先：maven.fabricmc.net 的 Cloudflare 从这边经常读超时
        boolean officialFirst = "official".equals(Config.downloadSource);
        if (officialFirst) candidates.add(base + path);
        candidates.add("https://bmclapi2.bangbang93.com/maven/" + path);
        candidates.add("https://maven.aliyun.com/repository/public/" + path);
        candidates.add(base + path);
        // Legacy Fabric（1.13 及以前）的 legacy LWJGL / intermediary 只在它自己的 maven 上
        candidates.add("https://maven.legacyfabric.net/" + path);

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
            if (last == null) return;
        }
        throw new IOException("下载库失败 " + label + ": " + (last == null ? "未知" : last.getMessage()));
    }

    // ---------------------------------------------------------------- Quilt

    static String installQuilt(Path gameDir, String mc, String want, Path java, Consumer<String> log)
            throws Exception {
        String loader = want;
        if (loader == null || loader.trim().isEmpty()) {
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
        if (ver == null || ver.trim().isEmpty()) {
            log.accept("查询 Forge 版本…");
            // 走 LoaderVersions：BMCLAPI 优先，官方 maven / promotions 兵底。
            // （以前这里直连 promotions_slim.json，国内经常读超时，26.1 就是这么挂的）
            java.util.List<LoaderVersions.Lv> list = LoaderVersions.fetch(Kind.FORGE, mc);
            if (list.isEmpty()) throw new IOException("Forge 没有 " + mc + " 的版本");
            ver = list.get(0).id();
        }
        String full = mc + "-" + ver;
        log.accept("Forge " + full);

        Path jar = gameDir.resolve(".mcver-tmp").resolve("forge-" + full + "-installer.jar");
        Files.createDirectories(jar.getParent());

        // Forge 的 maven 布局有三代，**目录名和文件名都得跟着变**：
        //   现代： net/minecraftforge/forge/<mc>-<ver>/forge-<mc>-<ver>-installer.jar
        //   1.7.10 后期（10.13.4.x）：
        //          net/minecraftforge/forge/<mc>-<ver>-<mc>/forge-<mc>-<ver>-<mc>-installer.jar
        //   1.7.2（branch = mc172）：
        //          net/minecraftforge/forge/<mc>-<ver>-mc172/forge-<mc>-<ver>-mc172-installer.jar
        //          —— 不是 `-1.7.2` 而是 `-mc172`，就这一代这么写。
        // 只拼现代那套会在 1.7.10 / 1.7.2 上 404 —— 而且 404 是**目录不存在**，不是文件缺失。
        String relModern = "net/minecraftforge/forge/" + full + "/forge-" + full + "-installer.jar";
        String relLegacy = "net/minecraftforge/forge/" + full + "-" + mc
                + "/forge-" + full + "-" + mc + "-installer.jar";
        String branch = "mc" + mc.replace(".", "");
        String relBranch = "net/minecraftforge/forge/" + full + "-" + branch
                + "/forge-" + full + "-" + branch + "-installer.jar";
        downloadFirst(new String[]{
                "https://mirrors.ustc.edu.cn/bmclapi/" + relModern,
                "https://cernet.mirrors.ustc.edu.cn/bmclapi/" + relModern,
                "https://bmclapi2.bangbang93.com/maven/" + relModern,
                "https://maven.minecraftforge.net/" + relModern,
                "https://mirrors.ustc.edu.cn/bmclapi/" + relLegacy,
                "https://cernet.mirrors.ustc.edu.cn/bmclapi/" + relLegacy,
                "https://bmclapi2.bangbang93.com/maven/" + relLegacy,
                "https://maven.minecraftforge.net/" + relLegacy,
                "https://mirrors.ustc.edu.cn/bmclapi/" + relBranch,
                "https://cernet.mirrors.ustc.edu.cn/bmclapi/" + relBranch,
                "https://bmclapi2.bangbang93.com/maven/" + relBranch,
                "https://maven.minecraftforge.net/" + relBranch
        }, jar);

        // 先把库里列的文件用我们自己的多源链下完，再让安装器 --offline 跑。
        // 为什么必要：Forge 安装器自己用 DownloadUtils 直连 maven.minecraftforge.net，
        // 没有重试/镜像/死主机记忆，国内一跳 SSL 握手超时就整个失败（1.20.6 就是这么挂的）。
        int prefetched = prefetchInstallerLibraries(gameDir, jar, log);
        log.accept("已预取 " + prefetched + " 个 Forge 库");

        try {
            run(java, log, "-jar", jar.toString(),
                    "--installClient", gameDir.toAbsolutePath().toString(), "--offline");
        } catch (Exception e) {
            if (looksLikeUnknownOption(e)) {
                // 1.7.10 / 1.10.2 那一代安装器**根本没有 --installClient**（只有 --extract /
                // --installServer），当年客户端安装只能靠 GUI。改走 install_profile 的
                // legacy（spec 0）格式：解出 universal jar + 直接写 versionInfo。
                log.accept("这批老安装器没有 --installClient，改用 legacy install_profile 流程");
                return installViaLegacyProfile(gameDir, jar, log);
            }
            // 踩过的坑（26.1 Forge，2026-10-26）：镜像链某一跳回 403，把错误页（14 KB）
            // 当成 mcp_config 的 zip 写进了 libraries/。下次预取虽然会按 sha1 判它不合格、
            // 重下正确文件，但**同一次运行**里那个坏文件已经被安装器读过 → 退出码 1。
            // 所以失败后先按清单把不合格的库文件清掉、重新预取一遍，再给一次离线机会。
            int purged = purgeInvalidLibraries(gameDir, jar, log);
            int again = prefetchInstallerLibraries(gameDir, jar, log);
            log.accept("离线安装失败（" + e.getMessage() + "）；清掉 " + purged
                    + " 个损坏的库文件，重新预取 " + again + " 个后重试一次");
            try {
                run(java, log, "-jar", jar.toString(),
                        "--installClient", gameDir.toAbsolutePath().toString(), "--offline");
            } catch (Exception e2) {
                // 还不行（多半是安装器还要拉我们没读到的文件）→ 退回镜像模式让它自己下
                log.accept("第二次离线安装仍失败（" + e2.getMessage() + "），改用镜像模式");
                run(java, log, "-jar", jar.toString(),
                        "--installClient", gameDir.toAbsolutePath().toString(),
                        "--mirror", "https://bmclapi2.bangbang93.com/maven/");
            }
        }
        // 版本 id 不要硬猜：1.20.2 是 forge-<mc>-<ver>，1.20.3 起变成 <mc>-forge<ver>
        // 而且**不能拿 MC 版本号当 hint**——versions/26.1 这种纯原版目录也包含 "26.1"，
        // 一旦它比刚装的 Forge 目录新，就会拿原版去启动（2026-10-26 真踩到：26.1 Forge
        // 的启动验证跑的是 net.minecraft.client.main.Main，属于假通过）。
        // 以 install_profile.json 里的 version 字段为准，那才是安装器真正写出来的 id。
        String profId = profileVersionId(jar);
        String found = null;
        if (profId != null && Files.isDirectory(gameDir.resolve("versions").resolve(profId))) {
            found = profId;
        }
        if (found == null) found = findFreshVersionId(gameDir, profId, ver);
        return found != null ? found : (profId != null ? profId : "forge-" + full);
    }

    /** install_profile.json 里的 {@code version}：安装器最终写出的版本 id（拿不准就返回 null）。 */
    static String profileVersionId(Path installerJar) {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(installerJar.toFile())) {
            String txt = zipText(z, "install_profile.json");
            if (txt == null) return null;
            String v = Json.str(Json.obj(Json.parse(txt)), "version", null);
            return v == null || v.trim().isEmpty() ? null : v.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 把安装器 jar 里声明要用的库全部先下好，返回成功个数。
     *
     * <p>为什么必须这么做：Forge / NeoForge 安装器内部的 DownloadUtils **只直连
     * maven.minecraftforge.net / maven.neoforged.net**，没有镜像、没有重试、没有死主机
     * 记忆，也不做相邻版本回退。国内网络一跳 SSL 握手超时就整个安装失败
     * （1.20.6 Forge、1.20.4/1.20.6 NeoForge 都是这么挂的）。
     *
     * <p>要读两份清单，缺一不可：
     * <ul>
     *   <li>{@code version.json} 的 libraries —— 装完要留在 classpath 里的运行时库</li>
     *   <li>{@code install_profile.json} 的 libraries / processors[].classpath / args 里的
     *       {@code [group:artifact:version]} —— 安装期处理器要用的工具链</li>
     * </ul>
     * 后者经常被漏，漏了就会在处理器阶段才炸。
     */
    /** 一条预取任务：落盘相对路径 + 候选地址 + 期望的 sha1/size（清单给了才校验）。 */
    private static final class Fetch {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String rel;
        public final List<String> urls;
        public final String sha1;
        public final long size;

        public Fetch(String rel, List<String> urls, String sha1, long size) {
            this.rel = rel;
            this.urls = urls;
            this.sha1 = sha1;
            this.size = size;
        }

        public String rel() { return rel; }
        public List<String> urls() { return urls; }
        public String sha1() { return sha1; }
        public long size() { return size; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Fetch)) return false;
            Fetch that = (Fetch) o;
            return true
                && java.util.Objects.equals(rel, that.rel)
                && java.util.Objects.equals(urls, that.urls)
                && java.util.Objects.equals(sha1, that.sha1)
                && size == that.size;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(rel),
                java.util.Objects.hashCode(urls),
                java.util.Objects.hashCode(sha1),
                size);
        }

        @Override
        public String toString() {
            return "Fetch[" + "rel=" + rel + ", " + "urls=" + urls + ", " + "sha1=" + sha1 + ", " + "size=" + size + "]";
        }
    }

    static int prefetchInstallerLibraries(Path gameDir, Path installerJar, Consumer<String> log) {
        // 关键：**不能**用 name 拼路径。Forge 1.14.4 的 name 写作
        //   net.minecraftforge:accesstransformers:1.0.5-shadowed
        // 看着像 version=1.0.5-shadowed，真实 url 却是
        //   accesstransformers/1.0.5/accesstransformers-1.0.5-shadowed.jar
        // （版本 1.0.5 + classifier shadowed），而清单里的 path 字段又是错的。
        Map<String, Fetch> fetches = new LinkedHashMap<>();
        try {
            collectAllFetches(installerJar, fetches);
        } catch (Exception e) {
            log.accept("读安装器清单失败：" + e.getMessage());
            return 0;
        }
        if (fetches.isEmpty()) return 0;

        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (Fetch f : fetches.values()) {
            Path dst = gameDir.resolve("libraries").resolve(f.rel());
            try {
                if (valid(dst, f)) { ok++; continue; }
                Files.createDirectories(dst.getParent());
                downloadVerified(f, dst);
                ok++;
            } catch (Exception ex) {
                failed.add(f.rel() + "（" + ex.getMessage() + "）");
            }
        }
        for (String f : failed) log.accept("  预取失败 " + f);
        return ok;
    }

    /**
     * 把安装器两份清单里声明的所有库收进 fetches（version.json + install_profile）。
     *
     * <p>预取和“清理损坏文件”共用这份逻辑，避免两边判定不一致。
     */
    private static void collectAllFetches(Path installerJar, Map<String, Fetch> fetches) throws Exception {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(installerJar.toFile())) {
            String verTxt = zipText(z, "version.json");
            if (verTxt != null) collectFetches(Json.obj(Json.parse(verTxt)), fetches);

            String profTxt = zipText(z, "install_profile.json");
            if (profTxt == null) return;
            Map<String, Object> prof = Json.obj(Json.parse(profTxt));
            collectFetches(prof, fetches);
            for (Object o : nz(Json.arr(prof.get("processors")))) {
                Map<String, Object> proc = Json.obj(o);
                if (proc == null) continue;
                for (Object c : nz(Json.arr(proc.get("classpath")))) {
                    addCoord(fetches, Json.str(c));
                }
                for (Object a : nz(Json.arr(proc.get("args")))) {
                    String s = Json.str(a);
                    if (s == null) continue;
                    s = s.trim();
                    if (s.startsWith("[") && s.endsWith("]")) {
                        String inner = s.substring(1, s.length() - 1).trim();
                        if (inner.split(":").length >= 3) addCoord(fetches, inner);
                    }
                }
            }
        }
    }

    /**
     * 按清单把 libraries/ 下不合格的文件删掉（镜像回 403 时会把错误页写成 .zip/.jar），
     * 返回删了多少个。安装器失败后调用，让下一次预取/重试从干净状态开始。
     */
    static int purgeInvalidLibraries(Path gameDir, Path installerJar, Consumer<String> log) {
        Map<String, Fetch> fetches = new LinkedHashMap<>();
        try {
            collectAllFetches(installerJar, fetches);
        } catch (Exception e) {
            return 0;
        }
        int purged = 0;
        for (Fetch f : fetches.values()) {
            Path dst = gameDir.resolve("libraries").resolve(f.rel());
            try {
                if (!Files.isRegularFile(dst)) continue;
                if (valid(dst, f)) continue;
                long was = Files.size(dst);
                Files.deleteIfExists(dst);
                purged++;
                log.accept("  删除损坏的库文件 " + f.rel() + "（实际 " + was + " 字节，清单 " + f.size() + "）");
            } catch (Exception ignored) { }
        }
        return purged;
    }

    /** 文件在不在、大小/哈希对不对。清单没给 size/sha1 时只要求非空且不是半截 jar。 */
    private static boolean valid(Path dst, Fetch f) {
        try {
            if (!Files.isRegularFile(dst)) return false;
            long len = Files.size(dst);
            if (len <= 0) return false;
            if (f.size() > 0 && len != f.size()) return false;
            if (f.sha1() != null) return f.sha1().equalsIgnoreCase(sha1(dst));
            // 没有权威大小：至少别把明显断掉的 jar 当好的
            return !f.rel().endsWith(".jar") || len > 4096 && Net.isJar(dst);
        } catch (Exception e) {
            return false;
        }
    }

    private static void downloadVerified(Fetch f, Path dst) throws Exception {
        Exception last = null;
        for (String u : f.urls()) {
            try {
                Net.download(u, dst, 10_000, 45_000);
                if (valid(dst, f)) return;
                // 下到半截（镜像提前断流很常见）→ 删掉换下一个源
                Files.deleteIfExists(dst);
                last = new IOException("文件不完整/校验失败: " + u);
            } catch (Exception e) {
                last = e;
                try { Files.deleteIfExists(dst); } catch (Exception ignored) { }
            }
        }
        throw last == null ? new IOException("没有可用的下载地址") : last;
    }

    private static String sha1(Path f) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
        try (java.io.InputStream in = Files.newInputStream(f)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 把一份清单里的 libraries（artifact + classifiers）收进 fetches。 */
    private static void collectFetches(Map<String, Object> json, Map<String, Fetch> out) {
        if (json == null) return;
        for (Object o : nz(Json.arr(json.get("libraries")))) {
            Map<String, Object> lib = Json.obj(o);
            if (lib == null) continue;

            Map<String, Object> dl = Json.obj(lib.get("downloads"));
            Map<String, Object> art = dl == null ? null : Json.obj(dl.get("artifact"));
            boolean got = addDownload(out, art);

            Map<String, Object> cls = dl == null ? null : Json.obj(dl.get("classifiers"));
            if (cls != null) {
                for (Object c : cls.values()) {
                    if (addDownload(out, Json.obj(c))) got = true;
                }
            }
            if (!got) addCoord(out, Json.str(lib, "name", null));
        }
    }

    /** downloads.artifact 里有 path/url 就用它们（url 的路径更可信），否则交给 addCoord。 */
    private static boolean addDownload(Map<String, Fetch> out, Map<String, Object> art) {
        if (art == null) return false;
        String rel = Json.str(art, "path", null);
        String url = Json.str(art, "url", null);
        if ((rel == null || rel.trim().isEmpty()) && url == null) return false;

        // path 和 url 的路径**经常不一致**，以 url 为准：
        //   path: .../1.0.5-shadowed/accesstransformers-1.0.5-shadowed.jar   （错）
        //   url : .../1.0.5/accesstransformers-1.0.5-shadowed.jar            （对）
        // 但落盘位置必须按 path —— 安装器就是去那个位置找。
        String relFromUrl = relOf(url);
        String key = (rel == null || rel.trim().isEmpty()) ? relFromUrl : rel;
        if (key == null) return false;

        // 镜像在前、清单里的原始 url 垫底。原 url 基本都指向 maven.minecraftforge.net，
        // 国内经常吊到读超时（120s），放第一个等于每个库都白等两分钟。
        List<String> urls = new ArrayList<>();
        if (relFromUrl != null) for (String u : mirrorUrls(relFromUrl)) if (!urls.contains(u)) urls.add(u);
        if (!key.equals(relFromUrl)) for (String u : mirrorUrls(key)) if (!urls.contains(u)) urls.add(u);
        if (url != null && !url.trim().isEmpty() && !urls.contains(url)) urls.add(url);

        String sha1 = Json.str(art, "sha1", null);
        long size = Json.num(art, "size", 0);
        merge(out, new Fetch(key, urls, sha1, size));
        return true;
    }

    /** 同一路径可能被 version.json 和 install_profile.json 各写一遍，合并候选地址。 */
    private static void merge(Map<String, Fetch> out, Fetch f) {
        Fetch old = out.get(f.rel());
        if (old == null) { out.put(f.rel(), f); return; }
        List<String> urls = new ArrayList<>(old.urls());
        for (String u : f.urls()) if (!urls.contains(u)) urls.add(u);
        String sha1 = old.sha1() != null ? old.sha1() : f.sha1();
        long size = old.size() > 0 ? old.size() : f.size();
        out.put(f.rel(), new Fetch(f.rel(), urls, sha1, size));
    }

    private static void addCoord(Map<String, Fetch> out, String coord) {
        if (coord == null || coord.split(":").length < 3) return;
        try {
            Mvn m = Mvn.parse(coord);
            merge(out, new Fetch(m.rel(), mirrorUrls(m.rel()), null, 0));
        } catch (Exception ignored) { }
    }

    /** https://host/path/to/file.jar → path/to/file.jar；解析不出来返回 null。 */
    private static String relOf(String url) {
        if (url == null || url.trim().isEmpty()) return null;
        try {
            String p = java.net.URI.create(url).getPath();
            if (p == null) return null;
            while (p.startsWith("/")) p = p.substring(1);
            return p.isEmpty() ? null : p;
        } catch (Exception e) {
            return null;
        }
    }
    /** 一个相对路径的镜像候选链（IPv4 优先，最后才是官方）。 */
    private static List<String> mirrorUrls(String rel) {
        List<String> urls = new ArrayList<>();
        String srcRoot = Sources.active().rootClean();
        if (!srcRoot.isEmpty()) urls.add(srcRoot + "/maven/" + rel);
        urls.add("https://mirrors.ustc.edu.cn/bmclapi/" + rel);
        urls.add("https://cernet.mirrors.ustc.edu.cn/bmclapi/" + rel);
        urls.add("https://bmclapi2.bangbang93.com/maven/" + rel);
        urls.add("https://maven.minecraftforge.net/" + rel);
        urls.add("https://repo1.maven.org/maven2/" + rel);
        urls.add("https://maven.neoforged.net/releases/" + rel);
        return urls;
    }

    private static void collectLibraries(Map<String, Object> json, Set<String> out) {
        if (json == null) return;
        for (Object o : nz(Json.arr(json.get("libraries")))) {
            Map<String, Object> lib = Json.obj(o);
            if (lib == null) continue;
            String name = Json.str(lib, "name", null);
            if (name != null && name.contains(":")) out.add(name);
        }
    }

    private static List<Object> nz(List<Object> l) {
        return l == null ? dev.mcver.core.Jdk.emptyList() : l;
    }

    /** 安装器报错是不是「不认识这个选项」（老 Forge 没有 --installClient）。 */
    private static boolean looksLikeUnknownOption(Exception e) {
        String m = e.getMessage();
        if (m == null) return false;
        String s = m.toLowerCase(java.util.Locale.ROOT);
        return s.contains("unrecognizedoption") || s.contains("not a recognized option") || s.contains("不认识这个选项");
    }

    /**
     * 老一代 Forge 安装器（1.7.10 / 1.10.2，install_profile 没有 spec/processors）的客户端安装。
     *
     * <p>那代安装器只支持 --extract / --installServer，客户端安装是 GUI 专属操作。
     * 但它把要做的三件事都写在 install_profile.json 里了：
     * <ol>
     *   <li>把 jar 里的 {@code install.filePath}（universal jar）放到
     *       {@code libraries/<install.path 的 maven 路径>}</li>
     *   <li>把 {@code versionInfo} 原样写成 {@code versions/<target>/<target>.json}</li>
     *   <li>versionInfo.libraries 里的库补齐（launchwrapper / asm / guava 等）</li>
     * </ol>
     */
    static String installViaLegacyProfile(Path gameDir, Path installerJar, Consumer<String> log) throws Exception {
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(installerJar.toFile())) {
            String txt = zipText(z, "install_profile.json");
            if (txt == null) throw new IOException("安装器里没有 install_profile.json");
            Map<String, Object> prof = Json.obj(Json.parse(txt));

            Map<String, Object> install = Json.obj(prof.get("install"));
            Map<String, Object> vi = Json.obj(prof.get("versionInfo"));
            if (install == null || vi == null) {
                throw new IOException("这不是 legacy（spec 0）install_profile，缺 install / versionInfo");
            }

            String filePath = Json.str(install, "filePath", null);
            String pathCoord = Json.str(install, "path", null);
            String target = Json.str(install, "target", Json.str(vi, "id", null));
            if (filePath == null || pathCoord == null || target == null) {
                throw new IOException("install_profile 字段不全（filePath / path / target）");
            }

            // 1) universal jar → libraries
            Mvn m = Mvn.parse(pathCoord);
            Path dst = m.file(gameDir);
            Files.createDirectories(dst.getParent());
            java.util.zip.ZipEntry e = z.getEntry(filePath);
            if (e == null) throw new IOException("安装器里没有 " + filePath);
            try (java.io.InputStream in = z.getInputStream(e)) {
                Files.copy(in, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.accept("已解出 " + filePath);

            // 2) versionInfo → versions/<target>/<target>.json
            //
            // 1.7.2 的 versionInfo **没有 inheritsFrom**（1.7.10 / 1.10.2 的都有），也没有
            // downloads / assets / assetIndex —— 它只是一层「补丁」，原版那半边（客户端 jar、
            // assetIndex、logging、javaVersion）全在 versions/<mc> 里。
            // 不留 inheritsFrom 的话，后续 flatten 合并出来的 json 会缺这些字段，
            // 或者 flatten 没跑（原版已经存在且被引用）时直接变成一个没有客户端 jar 的版本。
            String mcOfProfile = Json.str(install, "minecraft", null);
            if (mcOfProfile != null && Json.str(vi, "inheritsFrom", null) == null) {
                Map<String, Object> dl = vi.get("downloads") instanceof Map ? Json.obj(vi.get("downloads")) : null;
                boolean hasClient = dl != null && dl.get("client") != null;
                if (!hasClient) {
                    vi.put("inheritsFrom", mcOfProfile);
                    log.accept("  versionInfo 没写 inheritsFrom，补上 " + mcOfProfile);
                }
            }
            Path verDir = gameDir.resolve("versions").resolve(target);
            Files.createDirectories(verDir);
            dev.mcver.core.Jdk.writeString(verDir.resolve(target + ".json"), Json.write(vi), StandardCharsets.UTF_8);
            log.accept("写出版本 json: versions/" + target + "/" + target + ".json");

            // 3) versionInfo 里声明的库
            Map<String, Fetch> fetches = new LinkedHashMap<>();
            collectFetches(vi, fetches);
            int ok = 0;
            for (Fetch f : fetches.values()) {
                Path lib = gameDir.resolve("libraries").resolve(f.rel());
                try {
                    if (valid(lib, f)) { ok++; continue; }
                    Files.createDirectories(lib.getParent());
                    downloadVerified(f, lib);
                    ok++;
                } catch (Exception ex) {
                    log.accept("  库下载失败 " + f.rel() + "（" + ex.getMessage() + "）");
                }
            }
            log.accept("已准备 " + ok + "/" + fetches.size() + " 个库");
            return target;
        }
    }
    /**
     * 装完之后去 versions/ 里找刚生成的版本目录（名字里带任一 hint，多个命中取最新的）。
     * Forge/NeoForge 不同版本命名不一致，硬拼字符串会踩坑。
     *
     * <p>hint 必须是**加载器专属**的串（版本 id / 构建号），不能拿 MC 版本号当 hint，
     * 否则会命中纯原版目录。
     */
    static String findFreshVersionId(Path gameDir, String... hints) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        try (java.util.stream.Stream<java.nio.file.Path> ds = Files.list(dir)) {
            return ds.filter(Files::isDirectory)
                    .filter(d -> {
                        String n = d.getFileName().toString();
                        for (String h : hints) {
                            if (h != null && !h.trim().isEmpty() && n.contains(h)) return true;
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
        if (best == null || best.trim().isEmpty()) {
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
            // 和 Forge 一样的问题：NeoForge 安装器内部也直连 maven.neoforged.net，
            // 国内一跳超时就整个失败。先用我们自己的多源链把 version.json 里的库预取，
            // 再让安装器 --offline 跑。
            int prefetched = prefetchInstallerLibraries(gameDir, jar, log);
            log.accept("已预取 " + prefetched + " 个 NeoForge 库");
            try {
                run(java, log, "-jar", jar.toString(),
                        "--installClient", gameDir.toAbsolutePath().toString(), "--offline");
            } catch (IOException offErr) {
                // 同 Forge：先清掉被镜像错误页污染的库文件，重新预取，再给一次离线机会
                int purged = purgeInvalidLibraries(gameDir, jar, log);
                int again = prefetchInstallerLibraries(gameDir, jar, log);
                log.accept("离线安装失败（" + offErr.getMessage() + "）；清掉 " + purged
                        + " 个损坏的库文件，重新预取 " + again + " 个后重试一次");
                try {
                    run(java, log, "-jar", jar.toString(),
                            "--installClient", gameDir.toAbsolutePath().toString(), "--offline");
                } catch (IOException offErr2) {
                    log.accept("第二次离线安装仍失败（" + offErr2.getMessage() + "），退回在线模式");
                    run(java, log, "-jar", jar.toString(),
                            "--installClient", gameDir.toAbsolutePath().toString(),
                            "--mirror", "https://bmclapi2.bangbang93.com/maven/");
                }
            }
            String found = findFreshVersionId(gameDir, profileVersionId(jar), bare);
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
        if (id == null || id.trim().isEmpty()) throw new IOException("version.json 里没有 id");
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
            if (name != null && !name.trim().isEmpty()) coords.add(name);
        }
        for (Object o : Json.arr(profile.get("libraries"))) {
            Map<String, Object> lib = Json.obj(o);
            String name = Json.str(lib, "name", null);
            if (name != null && !name.trim().isEmpty()) coords.add(name);
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
                if (coord != null && !coord.trim().isEmpty()) cp.add(ensureMaven(gameDir, coord).toString());
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

    /** 同一个 artifact 的候选下载地址（当前下载源 → BMCLAPI 的 IPv4 镜像 → neoforged → forge → central）。 */
    private static List<String> mavenUrls(Mvn m) {
        String rel = m.rel();
        List<String> urls = new ArrayList<>();
        String srcRoot = Sources.active().rootClean();
        if (!srcRoot.isEmpty()) urls.add(srcRoot + "/maven/" + rel);
        // BMCLAPI 的 /maven/ 会 302 到 mirrors.cernet.edu.cn → ipv6.mirrors.ustc.edu.cn，
        // 而后者只有 AAAA 记录：机器 IPv6 不通时整条链死在 DNS 上（这就是
        // JarJarFileSystems 一直“Connect timed out”的真相）。直接走 USTC 的 IPv4 镜像，
        // 路径把 /maven/ 换成 /bmclapi/。
        urls.add("https://mirrors.ustc.edu.cn/bmclapi/" + rel);
        urls.add("https://cernet.mirrors.ustc.edu.cn/bmclapi/" + rel);
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
            String classifier = p.length > 3 && !p[3].trim().isEmpty() ? p[3].trim() : null;
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
        try (java.io.InputStream in = z.getInputStream(e)) {
            return dev.mcver.core.Jdk.readAllText(in);
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
                try (java.io.InputStream in = z.getInputStream(e)) {
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

        // ofId 为 null 表示「用推荐版」：下载页没指定版本时走这里，不能直接 NPE。
        if (ofId == null || ofId.trim().isEmpty()) {
            log.accept("查询 OptiFine 版本…");
            java.util.List<LoaderVersions.Lv> list = LoaderVersions.optifineList(mc);
            if (list.isEmpty()) throw new IOException("OptiFine 没有 " + mc + " 的版本");
            ofId = list.get(0).id();
            log.accept("选用 OptiFine " + ofId);
        }

        String safe = ofId.replace('/', '_');
        Path jar = gameDir.resolve(".mcver-tmp").resolve("OptiFine_" + mc + "_" + safe + ".jar");
        Files.createDirectories(jar.getParent());
        log.accept("下载 OptiFine " + mc + " " + ofId);
        download("https://bmclapi2.bangbang93.com/optifine/" + mc + "/" + ofId, jar);

        log.accept("静默运行 OptiFine 安装器…");
        invokeOptifineInstaller(jar, gameDir, mc, log);

        // 找出刚生成/更新的版本目录
        String id = newestOptifineVersion(gameDir, mc);
        if (id == null) throw new IOException("OptiFine 安装器没有生成版本目录");
        log.accept("OptiFine 版本: " + id);
        return id;
    }

    /**
     * 跑 OptiFine 安装器 —— **必须开子进程**。
     *
     * <p>1.12.2 的安装器是 Java 8 时代的类，而启动器自己通常跑在 Java 21 上；
     * 同一个 JVM 只能有一个 Java 版本，在进程内反射调用等于用错 Java。
     * 所以按这个 MC 版本要求的 Java 另起一个 JVM 跑 {@link OptifineRunner}。
     */
    private static void invokeOptifineInstaller(Path jar, Path gameDir, String mc, Consumer<String> log) throws Exception {
        Path java = desiredJava(mc);
        if (java == null) throw new IOException("找不到可用的 Java，跑不了 OptiFine 安装器");

        // 驱动类必须是 Java 8 字节码（1.12.2 的安装器只能在 Java 8 上跑），所以它单独编成
        // 一个 jar 放进 mod jar 当资源；这里解出来再用目标 Java 执行。
        Path runner = gameDir.resolve(".mcver-tmp").resolve("optifine-runner.jar");
        extractRunner(runner);

        log.accept("用 " + java.getFileName() + "（" + java + "）跑 OptiFine 安装器");
        run(java, log, "-cp", runner.toAbsolutePath().toString(), "dev.mcver.runner.OptifineRunner",
                jar.toAbsolutePath().toString(), gameDir.toAbsolutePath().toString());
        log.accept("OptiFine 安装器执行完毕");
    }

    /** 把打包进来的 optifine-runner.jar 解到磁盘（每次覆盖，保证跟当前版本一致）。 */
    private static void extractRunner(Path dst) throws IOException {
        Files.createDirectories(dst.getParent());
        try (java.io.InputStream in = LoaderInstaller.class.getResourceAsStream("/optifine-runner.jar")) {
            if (in == null) throw new IOException("mod 里缺少 optifine-runner.jar 资源（构建时没生成？）");
            Files.copy(in, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 这个 MC 版本该用哪个 Java 跑安装器（老版本 8，1.17+ 17，1.20.5+ 21）。 */
    static Path desiredJava(String mc) {
        int need = 8;
        if (compareVersions(mc, "1.20.5") >= 0) need = 21;
        else if (compareVersions(mc, "1.18") >= 0) need = 17;
        else if (compareVersions(mc, "1.17") >= 0) need = 16;
        Path p = JavaFinder.forMajor(need);
        if (p != null) return p;
        return JavaFinder.highest();
    }


    private static String newestOptifineVersion(Path gameDir, String mc) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        String best = null;
        long bestTime = -1;
        try (java.util.stream.Stream<java.nio.file.Path> ds = Files.list(dir)) {
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
        if (wantIrisVer != null && !wantIrisVer.trim().isEmpty()) {
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
        String json = "{\n"
                + "  \"profiles\": {},\n"
                + "  \"settings\": { \"enableSnapshots\": false },\n"
                + "  \"version\": 3\n"
                + "}\n";
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

    /**
     * 是不是「这个主机根本连不上」。
     *
     * <p>注意 <b>Read timed out 不算</b>：那是连上了但慢，拉黑等于把这个主机剩下的
     * 所有库都判死刑。1.14.4 Forge 就是这么挂的 —— maven.minecraftforge.net 第一次
     * 读超时被拉黑，之后两个只有它有正确路径的 classifier 库就再也下不到了。
     */
    private static boolean looksLikeNetworkFailure(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        if (m.contains("read timed out")) return false;
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
        boolean unknownOption = false;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                log.accept(line);
                String s = line.toLowerCase(java.util.Locale.ROOT);
                if (s.contains("unrecognizedoptionexception") || s.contains("not a recognized option")) {
                    unknownOption = true;
                }
            }
        }
        int code = proc.waitFor();
        if (code != 0) {
            // 把「不认识这个选项」这件事带出去：老 Forge 安装器没有 --installClient，
            // 上层要靠它决定改走 legacy install_profile 流程。
            throw new IOException("installer 退出码 " + code + (unknownOption ? "（该安装器不认识这个选项）" : ""));
        }
    }

    /** 从已安装目录里找出某 MC 版本对应的 loader 版本 id（用于提示）。 */
    public static String findInstalledLoaderId(Path gameDir, String mc) {
        Path dir = gameDir.resolve("versions");
        if (!Files.isDirectory(dir)) return null;
        try (java.util.stream.Stream<java.nio.file.Path> ds = Files.list(dir)) {
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
