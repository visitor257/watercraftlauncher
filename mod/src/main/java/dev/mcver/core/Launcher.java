package dev.mcver.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 游戏内启动器核心：读本地 version json（含 inheritsFrom 链合并）→ 拼命令行 → 起子进程。
 *
 * 只用已装在磁盘上的文件，不联网。缺文件时给出 warnings，不静默失败。
 */
public final class Launcher {

    private Launcher() { }

    /** 一个版本的启动计划。 */
    public static final class Plan {
        public Path gameDir;
        /** 真正传给 --gameDir 的目录：开了版本隔离就是 versions/<id>。 */
        public Path runDir;
        public String versionId;
        public String mainClass;
        public String type = "release";
        public String assetIndexId;
        public int requiredJava = 8;
        /** 这个版本要哪个 Mojang 运行时组件（javaVersion.component），没写就 null */
        public String javaComponent;
        public Path nativesDir;
        public List<Path> classpath = new ArrayList<>();
        public List<String> jvm = new ArrayList<>();
        public List<String> game = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public Map<String, Object> json = Map.of();

        public int assetMissing;
        public List<Path> missing = new ArrayList<>();
        public boolean ready() { return mainClass != null && !classpath.isEmpty(); }
    }

    // ================================================================ 版本枚举

    /**
     * 安装没走完时留在版本目录里的标记文件。
     * 安装一开始就写，跑完才删 —— 「启动」页靠它把半截版本藏起来。
     */
    public static final String INCOMPLETE = ".mcver-incomplete";

    /**
     * 版本隔离标记：有它就说明这个版本用 versions/&lt;id&gt; 当游戏目录启动
     * （存档 / mods / 配置各自一份，assets 和 libraries 仍然共用）。
     */
    public static final String ISOLATED = ".mcver-isolated";

    /**
     * 隐藏标记：装模组加载器时顺带下的原版会被打上这个，
     * 这样列表里就只剩 1.20.1-Fabric，不会再冒出一个 1.20.1。
     * 文件不能删 —— 加载器版本靠 inheritsFrom 引用它的 jar 和 json。
     */
    public static final String HIDDEN = ".mcver-hidden";

    /** 这个版本是不是被隐藏了（仍然存在磁盘上）。 */
    public static boolean isHidden(Path gameDir, String id) {
        return id != null && Files.isRegularFile(
                gameDir.resolve("versions").resolve(id).resolve(HIDDEN));
    }

    /** 显式开关隐藏。 */
    public static void setHidden(Path gameDir, String id, boolean on) throws IOException {
        Path verDir = gameDir.resolve("versions").resolve(id);
        Path marker = verDir.resolve(HIDDEN);
        if (on) {
            Files.createDirectories(verDir);
            Files.writeString(marker, java.time.Instant.now().toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(marker);
        }
    }

    /** 这个版本开没开版本隔离。 */
    public static boolean isIsolated(Path gameDir, String id) {
        return id != null && Files.isRegularFile(
                gameDir.resolve("versions").resolve(id).resolve(ISOLATED));
    }

    /** 开/关版本隔离（关的时候只摘标记，不删文件）。 */
    public static void setIsolated(Path gameDir, String id, boolean on) throws IOException {
        Path verDir = gameDir.resolve("versions").resolve(id);
        Files.createDirectories(verDir);
        Path marker = verDir.resolve(ISOLATED);
        if (on) {
            for (String sub : new String[]{"mods", "saves", "resourcepacks", "shaderpacks", "config", "logs"}) {
                Files.createDirectories(verDir.resolve(sub));
            }
            Files.writeString(marker, java.time.Instant.now().toString() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } else {
            Files.deleteIfExists(marker);
        }
    }

    /** 列出 gameDir 下**装完**、且没被隐藏的版本（启动页 / 版本页用）。 */
    public static List<String> installed(Path gameDir) {
        List<String> out = new ArrayList<>();
        for (String id : installedAll(gameDir)) {
            if (isComplete(gameDir, id) && !isHidden(gameDir, id)) out.add(id);
        }
        return out;
    }

    /** 被隐藏的（一般是加载器版本引用的原版）。 */
    public static List<String> hidden(Path gameDir) {
        List<String> out = new ArrayList<>();
        for (String id : installedAll(gameDir)) {
            if (isComplete(gameDir, id) && isHidden(gameDir, id)) out.add(id);
        }
        return out;
    }

    /** 哪个版本的 json 里 inheritsFrom 指向了 vanillaId（没找到返回 null）。 */
    public static String parentOf(Path gameDir, String vanillaId) {
        for (String id : installedAll(gameDir)) {
            if (id.equals(vanillaId)) continue;
            Map<String, Object> raw = readVersionJson(gameDir, id);
            if (raw == null) continue;
            if (vanillaId.equals(json_str(raw.get("inheritsFrom")))) return id;
        }
        return null;
    }

    /**
     * 把「加载器版本 + 它继承的原版」合并成**一个**版本目录，然后把原版目录删掉。
     *
     * 很多启动器装完 1.20.1 + Fabric 会留两个目录（官方启动器、PCL 也这样），
     * 因为加载器版本的 json 只写 `inheritsFrom`，客户端 jar 还在原版目录里。
     * 这里把继承链展开写进 loaderId 自己的 json，把原版 jar 搬（硬链接，搬不动就复制）
     * 到 loaderId 目录下，然后删原版目录 —— 磁盘上就只剩 1.20.1-Fabric 了。
     *
     * 保守条件：
     * - 原版目录必须存在，且不能还有别的版本引用它；
     * - json 写得出来、jar 到位了，才删原版目录。
     */
    public static boolean flatten(Path gameDir, String loaderId, String vanillaId) throws Exception {
        Path vdir = gameDir.resolve("versions");
        Path loaderDir = vdir.resolve(loaderId);
        Path vanillaDir = vdir.resolve(vanillaId);
        if (!Files.isDirectory(loaderDir) || !Files.isDirectory(vanillaDir)) return false;

        // 别的版本还引用着就不能删
        for (String id : installedAll(gameDir)) {
            if (id.equals(vanillaId) || id.equals(loaderId)) continue;
            Map<String, Object> raw = readVersionJson(gameDir, id);
            if (raw != null && vanillaId.equals(json_str(raw.get("inheritsFrom")))) return false;
        }

        Map<String, Object> flat = merged(gameDir, loaderId, new HashSet<>());
        flat.put("id", loaderId);
        flat.remove("inheritsFrom");

        Files.createDirectories(loaderDir);
        Files.writeString(loaderDir.resolve(loaderId + ".json"), Json.write(flat), StandardCharsets.UTF_8);

        Path srcJar = vanillaDir.resolve(vanillaId + ".jar");
        if (Files.isRegularFile(srcJar)) {
            Path dstJar = loaderDir.resolve(loaderId + ".jar");
            if (!Files.exists(dstJar)) {
                try {
                    Files.createLink(dstJar, srcJar);            // 同一分区��硬链接，不占额外空间
                } catch (Exception e) {
                    Files.copy(srcJar, dstJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        // 自检：json 能解析、jar 找得到，才敢删原版
        if (readVersionJson(gameDir, loaderId) == null) return false;
        if (!Files.isRegularFile(loaderDir.resolve(loaderId + ".jar"))) {
            Path inherited = vdir.resolve(vanillaId).resolve(vanillaId + ".jar");
            if (Files.isRegularFile(inherited)) return false;
        }

        deleteTree(vanillaDir);
        return true;
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var s = Files.walk(root)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 列出所有有 json 的版本，包括没装完的和被隐藏的（版本页要能看到/能删）。 */
    public static List<String> installedAll(Path gameDir) {
        List<String> out = new ArrayList<>();
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) return out;
        try (var s = Files.list(versions)) {
            s.filter(Files::isDirectory).forEach(d -> {
                String id = d.getFileName().toString();
                if (Files.isRegularFile(d.resolve(id + ".json")) || hasAnyJson(d)) out.add(id);
            });
        } catch (IOException ignored) { }
        out.sort(String.CASE_INSENSITIVE_ORDER.reversed());
        return out;
    }

    /** 没装完的（半截下载 / 安装中途崩掉）。 */
    public static List<String> unfinished(Path gameDir) {
        List<String> out = new ArrayList<>();
        for (String id : installedAll(gameDir)) {
            if (!isComplete(gameDir, id)) out.add(id);
        }
        return out;
    }

    /**
     * 这个版本算不算装完了：
     * 1. 没有 .mcver-incomplete 标记
     * 2. 版本 json 在，而且能解析
     * 3. 客户端 jar 找得到（自己目录里，或者顺着 inheritsFrom 找到父版本）
     *
     * 全量的库/资源校验太重（每次重建界面都要跑），交给 plan() 在启动时做。
     */
    public static boolean isComplete(Path gameDir, String id) {
        Path dir = gameDir.resolve("versions").resolve(id);
        if (!Files.isDirectory(dir)) return false;
        if (Files.isRegularFile(dir.resolve(INCOMPLETE))) return false;

        Map<String, Object> vj = readVersionJson(gameDir, id);
        if (vj == null) return false;

        // 客户端 jar：自己目录 / 显式 jar / 继承链上的
        String jarId = resolveJarId(gameDir, id, vj);
        Path jar = gameDir.resolve("versions").resolve(jarId).resolve(jarId + ".jar");
        if (Files.isRegularFile(jar)) return true;

        // 极老的版本 json 里可能没 client 下载项，也不继承，就不强求 jar
        Map<String, Object> dl = json_obj(vj.get("downloads"));
        boolean needsJar = dl != null && dl.get("client") != null;
        if (!needsJar && json_str(vj.get("inheritsFrom")) == null) return true;

        // 加载器版本：父版本还没装完就是没装完
        String parent = json_str(vj.get("inheritsFrom"));
        if (parent != null) return isComplete(gameDir, parent);
        return false;
    }

    /** 安装开始时正儿八经建目录 → 先摆标记，成功再摘。 */
    public static void markIncomplete(Path verDir) {
        try {
            Files.createDirectories(verDir);
            Files.writeString(verDir.resolve(INCOMPLETE),
                    java.time.Instant.now().toString() + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) { }
    }

    public static void markComplete(Path verDir) {
        try {
            Files.deleteIfExists(verDir.resolve(INCOMPLETE));
        } catch (Exception ignored) { }
    }

    /**
     * 客户端 jar 到底是谁的：
     * 1. json 里显式写了 "jar" → 用它
     * 2. 自己目录里有 id.jar → 自己
     * 3. 否则跟着 inheritsFrom 往上找，用最底层有 jar 的那个
     */
    private static String resolveJarId(Path gameDir, String versionId, Map<String, Object> merged) {
        String explicit = json_str(merged.get("jar"));
        if (explicit != null && !explicit.isBlank()) return explicit;

        Path verDir = gameDir.resolve("versions");
            if (Files.isRegularFile(verDir.resolve(versionId).resolve(versionId + ".jar"))) return versionId;

        String cur = versionId;
        Set<String> seen = new HashSet<>();
        while (cur != null && seen.add(cur)) {
            Map<String, Object> raw = readVersionJson(gameDir, cur);
            if (raw == null) break;
            String withJar = json_str(raw.get("jar"));
            if (withJar != null && !withJar.isBlank()) cur = withJar;
            if (Files.isRegularFile(verDir.resolve(cur).resolve(cur + ".jar"))) return cur;
            cur = json_str(raw.get("inheritsFrom"));
        }
        return versionId;
    }

    private static Map<String, Object> readVersionJson(Path gameDir, String id) {
        Path f = gameDir.resolve("versions").resolve(id).resolve(id + ".json");
        if (!Files.isRegularFile(f)) return null;
        try {
            return json_obj(Installer.Json.parse(Files.readString(f, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    private static Path firstJar(Path dir) {
        if (!Files.isDirectory(dir)) return null;
        try (var s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean hasAnyJson(Path dir) {
        try (var s = Files.list(dir)) {
            return s.anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    // ================================================================ json 合并

    /**
     * 加载器是不是自己带了客户端代码（那就不能再把原版 jar 塞进 classpath）：
     *
     * <p>1) mainClass 属于 BootstrapLauncher 家族（Forge 1.20.2+ / NeoForge 20.2+）；
     * <p>2) libraries/net/minecraft/client/&lt;ver&gt;/ 下真的有 client-*-srg.jar。
     *
     * <p>两条都满足才返回 true。Forge 1.20.1 用的是 cpw.mods.bootstraplauncher，
     * 而且没有 srg jar，客户端代码仍然只能来自原版 jar，不能误伤。
     */
    private static boolean clientJarProvidedByLoader(Path libs, String mainClass) {
        // 只针对 net.minecraftforge.bootstrap.BootstrapLauncher（Forge 1.20.2）
        // 和 cpw.mods.bootstraplauncher.BootstrapLauncher（NeoForge 20.x）。
        // ⚠️ Forge 1.20.3 的 mainClass 是 net.minecraftforge.bootstrap.ForgeBootstrap，
        //    它带着原版 jar 也能正常启动（实测到 Setting user），别一起排除，否则反而可能坏。
        if (mainClass == null || !mainClass.endsWith("BootstrapLauncher")) return false;
        Path dir = libs.resolve("net").resolve("minecraft").resolve("client");
        if (!Files.isDirectory(dir)) return false;
        try (java.util.stream.Stream<Path> s = Files.walk(dir, 3)) {
            return s.anyMatch(pp -> {
                String f = pp.getFileName().toString();
                return f.startsWith("client-") && f.endsWith("-srg.jar");
            });
        } catch (IOException e) {
            return false;
        }
    }

    public static Plan plan(Path gameDir, String versionId) throws Exception {
        Map<String, Object> j = merged(gameDir, versionId, new HashSet<>());

        // 客户端 jar 的归属版本：继承树里最底层的那个（有自己 jar 的祖先）
        String jarId = resolveJarId(gameDir, versionId, j);

        Plan p = new Plan();
        p.gameDir = gameDir;
        p.runDir = gameDir;
        // 版本隔离：真正把 --gameDir 换到 versions/<id>，不然建了 mods 目录也白建
        if (isIsolated(gameDir, versionId)) {
            p.runDir = gameDir.resolve("versions").resolve(versionId);
        }
        p.versionId = versionId;
        p.json = j;
        p.mainClass = json_str(j.get("mainClass"));
        p.type = j.get("type") == null ? "release" : json_str(j.get("type"));

        Map<String, Object> jv = json_obj(j.get("javaVersion"));
        if (jv != null && jv.get("majorVersion") != null) {
            p.requiredJava = (int) json_num(jv.get("majorVersion"));
        }
        if (jv != null && jv.get("component") != null) p.javaComponent = json_str(jv.get("component"));

        Map<String, Object> ai = json_obj(j.get("assetIndex"));
        p.assetIndexId = ai != null ? json_str(ai.get("id")) : json_str(j.get("assets"));
        if (p.assetIndexId == null) p.assetIndexId = "legacy";

        Path libs = gameDir.resolve("libraries");
        Path verDir = gameDir.resolve("versions").resolve(versionId);
        p.nativesDir = verDir.resolve("natives");

        // ---- libraries → classpath（旧版 natives 顺带解压）
        // 同一 group:artifact 只留版本最高的一个。继承合并后经常出现 asm 9.6 和 9.10.1 并存，
        // Fabric 的 verifyClasspath 会抛 "duplicate ASM classes found on classpath"，进程立刻退出码 1。
        Map<String, Map<String, Object>> byArtifact = new LinkedHashMap<>();
        for (Object o : json_arr(j.get("libraries"))) {
            Map<String, Object> lib = json_obj(o);
            if (lib == null) continue;
            Maven m = Maven.parse(json_str(lib.get("name")));
            if (m == null) continue;
            String key = m.key() + "|" + m.ext;
            Map<String, Object> prev = byArtifact.get(key);
            if (prev == null) {
                byArtifact.put(key, lib);
                continue;
            }
            Maven pm = Maven.parse(json_str(prev.get("name")));
            if (pm != null && LoaderInstaller.compareVersions(m.version, pm.version) > 0) {
                byArtifact.put(key, lib);
            } else if (pm != null && LoaderInstaller.compareVersions(m.version, pm.version) == 0
                    && hasNativesInfo(lib) && !hasNativesInfo(prev)) {
                // 同版本的两个条目：优先留带 natives/classifiers 的那个。
                // 否则 Fabric/Quilt profile 里那份「不含 natives」的 lwjgl 会挤出原版那份，
                // 导致 1.13~1.18 拿不到 native 库 → UnsatisfiedLinkError: lwjgl.dll
                byArtifact.put(key, lib);
            }
        }

        Set<Path> cp = new LinkedHashSet<>();
        Set<Path> nativesDone = new HashSet<>();
        for (Map<String, Object> lib : byArtifact.values()) {
            if (!Installer.rulesAllow(json_arrOrNull(lib.get("rules")), Installer.noFeatures())) continue;

            String name = json_str(lib.get("name"));
            Maven m = Maven.parse(name);

            Map<String, Object> dl = json_obj(lib.get("downloads"));
            Map<String, Object> natives = json_obj(lib.get("natives"));

            // ≤1.18：natives 走 classifiers，解压到 natives 目录；
            // 但普通 jar（比如 lwjgl-2.9.4.jar）仍然要进 classpath，所以不能 continue
            String classifier = natives == null ? null : json_str(natives.get(Installer.OS_NAME));
            if (classifier == null && dl != null) {
                // 兼容：有些合并后的 json（如 Fabric profile 合并进 1.16.5）
                // 只留了 downloads.classifiers，把 natives 块弄丢了 → 按惯例推断
                Map<String, Object> cls0 = json_obj(dl.get("classifiers"));
                if (cls0 != null) {
                    String guess = "natives-" + Installer.OS_NAME;
                    if (cls0.containsKey(guess)) classifier = guess;
                }
            }
            if (classifier != null) {
                classifier = classifier.replace("${arch}", "64");
                Map<String, Object> cls = dl == null ? null : json_obj(dl.get("classifiers"));
                Map<String, Object> c = cls == null || classifier == null ? null : json_obj(cls.get(classifier));
                String path = c == null ? null : json_str(c.get("path"));
                if (path != null) {
                    Path jar = libs.resolve(path);
                    if (Files.isRegularFile(jar)) {
                        try {
                            Files.createDirectories(p.nativesDir);
                            // 按 jar 路径去重：natives 往往有多个 jar（lwjgl / glfw / openal / stb …），
                            // 每个都要解，不能只解第一个
                            if (nativesDone.add(jar)) {
                                Installer.extractZip(jar, p.nativesDir);
                            }
                        } catch (IOException e) {
                            p.warnings.add("natives 解压失败: " + jar.getFileName());
                        }
                    } else {
                        p.missing.add(jar);
                    }
                }
            }

            Path jar = artifactPath(libs, dl, m);
            if (jar == null) continue;
            if (Net.isJar(jar)) {
                cp.add(jar);
                // 1.13~1.18：natives 是独立 classifier 库（org.lwjgl:lwjgl:3.2.2:natives-windows），
                // 官方启动器会把它解到 natives 目录并靠 -Djava.library.path 找。
                // 不解的话 1.16.5 会报 UnsatisfiedLinkError: Failed to locate library: lwjgl.dll。
                if (name != null && name.contains(":natives-")) {
                    try {
                        Files.createDirectories(p.nativesDir);
                        if (nativesDone.add(jar)) {
                            Installer.extractZip(jar, p.nativesDir);
                        }
                    } catch (IOException e) {
                        p.warnings.add("natives 解压失败: " + jar.getFileName());
                    }
                }
            } else if (natives == null) {
                // 带 natives 块的库（lwjgl-platform / jinput-platform）本来就只有 classifier jar，
                // 没有普通 jar，不算「缺文件」，不要吓用户
                p.missing.add(jar);
            }
        }

        // ---- client jar
        // Forge 1.20.2+ / NeoForge 20.2+：加载器自己已经带了一份客户端代码（
        // libraries/net/minecraft/client/client-<ver>-srg.jar，由安装器展开），
        // 再把原版 versions/<id>/<id>.jar 塞进 -cp，它就会变成一个自动模块名（如 _1._20._2），
        // 和 Forge 的 minecraft 模块抢同一个包 → java.lang.module.ResolutionException，
        // 窗口闪一下（约 1 秒）就退，日志里只有 “Module minecraft contains package ...”。
        boolean loaderOwnsClient = clientJarProvidedByLoader(libs, p.mainClass);
        Path clientJar = gameDir.resolve("versions").resolve(jarId).resolve(jarId + ".jar");
        if (!Files.isRegularFile(clientJar)) {
            // 再退一步：版本目录里有没有别的 jar
            clientJar = firstJar(gameDir.resolve("versions").resolve(versionId));
        }
        if (loaderOwnsClient) {
            // core 层不依赖 Minecraft / mod 类（能单独编出来跑），所以这里不写日志。
            // 想确认这条生效：看 logs/mcver-args-*.txt 的 -cp 里没有 versions/<id>/<id>.jar 就行。
            if (clientJar == null || !Net.isJar(clientJar)) {
                p.warnings.add("原版 jar 不在，且加载器也没带客户端代码");
            }
        } else if (clientJar != null && Net.isJar(clientJar)) {
            cp.add(clientJar);
        } else {
            p.missing.add(gameDir.resolve("versions").resolve(jarId).resolve(jarId + ".jar"));
        }

        p.classpath = new ArrayList<>(cp);
        // ---- 参数
        Path assetsRoot = gameDir.resolve("assets");
        Map<String, String> ph = new HashMap<>();
        ph.put("auth_player_name", "${player}");
        ph.put("version_name", versionId);
        ph.put("game_directory", p.runDir.toString());
        ph.put("assets_root", assetsRoot.toString());
        ph.put("game_assets", assetsRoot.resolve("virtual").resolve(p.assetIndexId).toString());
        ph.put("assets_index_name", p.assetIndexId);
        ph.put("auth_uuid", "${uuid}");
        ph.put("auth_access_token", "${token}");
        ph.put("auth_session", "${token}");
        ph.put("user_type", "msa");
        ph.put("version_type", p.type);
        ph.put("natives_directory", p.nativesDir.toString());
        ph.put("launcher_name", "mcver");
        ph.put("launcher_version", "1.0");
        ph.put("classpath", Installer.joinCp(p.classpath));
        ph.put("classpath_separator", java.io.File.pathSeparator);
        ph.put("library_directory", libs.toString());
        ph.put("auth_xuid", "0");
        ph.put("clientid", "0");
        ph.put("user_properties", "{}");
        ph.put("resolution_width", "854");
        ph.put("resolution_height", "480");

        Map<String, Object> args = json_obj(j.get("arguments"));
        if (args != null) {
            p.jvm.addAll(strArgs(json_arr(args.get("jvm")), ph));
            p.game.addAll(strArgs(json_arr(args.get("game")), ph));
        }
        String legacy = json_str(j.get("minecraftArguments"));
        if (p.game.isEmpty() && legacy != null) {
            for (String s : legacy.trim().split("\\s+")) {
                if (!s.isEmpty()) p.game.add(Installer.sub(s, ph));
            }
        }

        // 老版本没有 arguments.jvm：自己补
        if (p.jvm.isEmpty()) {
            p.jvm.add("-Djava.library.path=" + p.nativesDir);
            p.jvm.add("-cp");
            p.jvm.add(ph.get("classpath"));
        }
        if (!hasClasspath(p.jvm)) {
            p.jvm.add("-cp");
            p.jvm.add(ph.get("classpath"));
        }

        if (p.mainClass == null) p.warnings.add("json 里没有 mainClass");
        if (p.game.isEmpty()) p.warnings.add("没有游戏参数");
        p.assetMissing = countMissingAssets(gameDir, p.assetIndexId);
        return p;
    }

    /** 资源对象缺多少个（index 里列了但 objects 里没有）。 */
    static int countMissingAssets(Path gameDir, String indexId) {
        if (indexId == null) return 0;
        Path idx = gameDir.resolve("assets").resolve("indexes").resolve(indexId + ".json");
        if (!Files.isRegularFile(idx)) return -1;
        try {
            Map<String, Object> index = json_obj(Installer.Json.parse(Files.readString(idx, StandardCharsets.UTF_8)));
            Map<String, Object> objects = index == null ? null : json_obj(index.get("objects"));
            if (objects == null) return 0;
            Path root = gameDir.resolve("assets").resolve("objects");
            int missing = 0;
            for (Object v : objects.values()) {
                String h = json_str(json_obj(v).get("hash"));
                if (h == null || h.length() < 2) continue;
                if (!Files.isRegularFile(root.resolve(h.substring(0, 2)).resolve(h))) missing++;
            }
            return missing;
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean hasClasspath(List<String> jvm) {
        for (String a : jvm) if (a.equals("-cp") || a.equals("-classpath")) return true;
        return false;
    }

    private static boolean isEmptyDir(Path dir) throws IOException {
        try (var s = Files.list(dir)) { return s.findAny().isEmpty(); }
    }

    private static List<String> strArgs(List<Object> list, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String s) {
                out.add(Installer.sub(s, ph));
            } else if (o instanceof Map<?, ?> raw) {
                Map<String, Object> rule = json_obj(raw);
                if (rule == null) continue;
                if (!Installer.rulesAllow(json_arrOrNull(rule.get("rules")), Installer.noFeatures())) continue;
                Object v = rule.get("value");
                if (v instanceof String s) out.add(Installer.sub(s, ph));
                else for (Object e : json_arr(v)) out.add(Installer.sub(json_str(e), ph));
            }
        }
        return out;
    }

    private static Path artifactPath(Path libs, Map<String, Object> dl, Maven m) {
        Map<String, Object> art = dl == null ? null : json_obj(dl.get("artifact"));
        String path = art == null ? null : json_str(art.get("path"));
        if (path != null) return libs.resolve(path);
        return m == null ? null : libs.resolve(m.toPath());
    }

    // ================================================================ 合并

    private static Map<String, Object> merged(Path gameDir, String id, Set<String> seen) throws IOException {
        Map<String, Object> child = loadJson(gameDir, id);
        Map<String, Object> out = new LinkedHashMap<>(child);

        String parentId = json_str(child.get("inheritsFrom"));
        if (parentId == null || !seen.add(parentId)) return out;

        Map<String, Object> parent = merged(gameDir, parentId, seen);

        out = new LinkedHashMap<>(parent);
        for (String k : new String[]{"id", "mainClass", "assets", "assetIndex", "downloads",
                "javaVersion", "type", "minecraftArguments", "logging", "complianceLevel"}) {
            if (child.get(k) != null) out.put(k, child.get(k));
        }

        // libraries：父在前，子覆盖同 group:artifact:classifier
        List<Object> libs = new ArrayList<>(json_arr(parent.get("libraries")));
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < libs.size(); i++) {
            Maven m = Maven.parse(json_str(json_obj(libs.get(i)).get("name")));
            if (m != null) idx.put(m.key(), i);
        }
        for (Object o : json_arr(child.get("libraries"))) {
            Maven m = Maven.parse(json_str(json_obj(o).get("name")));
            Integer at = m == null ? null : idx.get(m.key());
            if (at != null) libs.set(at, o);
            else {
                if (m != null) idx.put(m.key(), libs.size());
                libs.add(o);
            }
        }
        out.put("libraries", libs);

        // arguments：父 + 子
        Map<String, Object> pa = json_obj(parent.get("arguments"));
        Map<String, Object> ca = json_obj(child.get("arguments"));
        if (pa != null || ca != null) {
            List<Object> jvm = new ArrayList<>();
            List<Object> game = new ArrayList<>();
            if (pa != null) {
                jvm.addAll(json_arr(pa.get("jvm")));
                game.addAll(json_arr(pa.get("game")));
            }
            if (ca != null) {
                jvm.addAll(json_arr(ca.get("jvm")));
                game.addAll(json_arr(ca.get("game")));
            }
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("jvm", jvm);
            a.put("game", game);
            out.put("arguments", a);
        }
        return out;
    }

    private static Map<String, Object> loadJson(Path gameDir, String id) throws IOException {
        Path dir = gameDir.resolve("versions").resolve(id);
        Path f = dir.resolve(id + ".json");
        if (!Files.isRegularFile(f) && Files.isDirectory(dir)) {
            try (var s = Files.list(dir)) {
                f = s.filter(x -> x.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .findFirst().orElse(null);
            }
        }
        if (f == null || !Files.isRegularFile(f)) {
            throw new IOException("找不到版本 json: " + id);
        }
        return json_obj(Installer.Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
    }

    // ================================================================ 启动

    /**
     * 选 java。顺序：设置里指定的（但**必须满足版本要求**）→ 自动按主版本挑 → 当前 JVM。
     *
     * 以前指定了就不管版本直接用：用户以前给老版本设过 Java 8，后来启动 1.21.4 时
     * 依旧拿 Java 8 去跑，只在界面上多一行小字提示，结果就是“有 pid 没窗口”。
     */
    public static Path pickJava(Plan p, List<String> notes) {
        String custom = Config.javaPath;
        if (custom != null && !custom.isBlank()) {
            Path cp = JavaFinder.resolveExe(custom);
            if (cp != null) {
                int m = JavaFinder.majorOfExe(cp);
                if (m <= 0 || m >= p.requiredJava) {
                    notes.add("Java " + m);
                    return cp;
                }
                notes.add("指定的 Java " + m + " 低于版本要求的 " + p.requiredJava + "，改用自动选择");
            } else {
                notes.add("指定的 Java 不可用，改用自动选择");
            }
        }
        Path found = JavaFinder.forMajor(p.requiredJava);
        if (found != null) return found;

        // 本机没有这个主版本 → 下 Mojang 官方运行时（否则 1.20.5+ / 26.x 直接卡死）
        Path fetched = JavaRuntime.ensure(p.gameDir, p.javaComponent, p.requiredJava, notes::add);
        if (fetched != null) {
            notes.add("自动下载 Java " + p.requiredJava + " 运行时");
            return fetched;
        }

        // 没一个满足要求：退到当前 JVM（它自己肯定是对的那个版本）
        Path self = Paths.get(System.getProperty("java.home", ""), "bin",
                Installer.OS_NAME.equals("windows") ? "java.exe" : "java");
        if (Files.isRegularFile(self)) {
            notes.add("没找到 Java " + p.requiredJava + "，改用当前 JVM");
            return self;
        }
        return null;
    }

    public static final class Running {
        public Process process;
        public Path logFile;
        public String command;
        public Path java;
        public String javaNote;
    }

    public static Running launch(Plan p, String player, String uuid, String token, int memMb) throws IOException {
        List<String> notes = new ArrayList<>();
        Path java = pickJava(p, notes);
        if (java == null) throw new IOException("找不到可用的 Java");

        int javaMajor = JavaFinder.majorOfExe(java);
        if (javaMajor > 0 && javaMajor < p.requiredJava) {
            // 版本不够就别硬起了 —— Java 会直接报 UnsupportedClassVersionError 之类的天书
            throw new IOException("这个版本要 Java " + p.requiredJava + "+，现在选的是 Java " + javaMajor
                    + "（" + java + "）。去「Java」页指定一个 " + p.requiredJava + " 以上的");
        }
        if (javaMajor > 0 && javaMajor != p.requiredJava) {
            notes.add("Java " + javaMajor + "，版本要 " + p.requiredJava);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add(java.toString());
        boolean hasXmx = p.jvm.stream().anyMatch(a -> a.startsWith("-Xmx"));
        if (!hasXmx && memMb > 0) cmd.add("-Xmx" + memMb + "M");
        cmd.add("-Dminecraft.launcher.brand=mcver");
        cmd.add("-Dminecraft.launcher.version=1.0");
        for (String a : p.jvm) {
            cmd.add(a
                    .replace("${player}", player)
                    .replace("${uuid}", uuid)
                    .replace("${token}", token));
        }
        cmd.add(p.mainClass);
        for (String a : p.game) {
            cmd.add(a
                    .replace("${player}", player)
                    .replace("${uuid}", uuid)
                    .replace("${token}", token));
        }

        Path logs = p.runDir.resolve("logs");
        Files.createDirectories(logs);
        String stamp = String.valueOf(System.currentTimeMillis());
        Path logFile = logs.resolve("mcver-launch-" + p.versionId + "-" + stamp + ".log");
        Path argFile = logs.resolve("mcver-args-" + stamp + ".txt");
        writeArgFile(argFile, cmd.subList(1, cmd.size()));

        // @argfile 是 Java 9+ 才有的语法。1.12.2 及更早的版本要 Java 8，
        // 直接传 @file 会被当成主类 → “找不到或无法加载主类 @…\mcver-args-xxx.txt”。
        ProcessBuilder pb;
        if (javaMajor > 0 && javaMajor <= 8) {
            List<String> direct = new ArrayList<>(cmd);
            int len = 0;
            for (String s : direct) len += s.length() + 1;
            if (len > 30000) {
                throw new IOException("Java 8 不支持 @argfile，而命令行长度 " + len
                        + " 超过 Windows 限制。请在设置里把 classpath 精简，或改用 Java 17+");
            }
            pb = new ProcessBuilder(direct);
        } else {
            pb = new ProcessBuilder(java.toString(), "@" + argFile.toAbsolutePath());
        }
        pb.directory(p.runDir.toFile());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        Running r = new Running();
        r.process = pb.start();
        r.logFile = logFile;
        r.java = java;
        r.command = String.join(" ", cmd);
        r.javaNote = String.join("；", notes);

        // 启动即退：多半是 Java 版本/参数不对，直接把日志尾巴报出来，别让用户干等
        try {
            if (r.process.waitFor(1800, TimeUnit.MILLISECONDS)) {
                int code = r.process.exitValue();
                String tail = tail(logFile, 5);
                throw new IOException("进程立刻退出了（代码 " + code + "）"
                        + (tail.isBlank() ? "，日志里没输出" : "：" + tail)
                        + "  日志: " + logFile);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        return r;
    }

    /** 日志摘要：优先给「根因那一行」，再补最后几行。 */
    private static String tail(Path log, int n) {
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();

            // 先找第一条像样的错误行（堆栈最顶上那句才是原因，最后几行通常全是 "at ..."）
            for (String raw : lines) {
                String s = raw.trim();
                if (s.isEmpty() || s.startsWith("at ") || s.startsWith("... ")) continue;
                if (s.contains("Exception") || s.contains("Error") || s.contains("Caused by")
                        || s.contains("Could not") || s.contains("Unrecognized")) {
                    sb.append(s.length() > 240 ? s.substring(0, 240) + "…" : s);
                    break;
                }
            }

            int from = Math.max(0, lines.size() - n);
            for (int i = from; i < lines.size(); i++) {
                String s = lines.get(i).trim();
                if (s.isEmpty()) continue;
                if (sb.length() > 0) sb.append(" / ");
                sb.append(s.length() > 180 ? s.substring(0, 180) + "…" : s);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 参数文件，绕开 Windows 32k 命令行上限。 */
    private static void writeArgFile(Path file, List<String> args) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String a : args) {
            sb.append('"');
            for (int i = 0; i < a.length(); i++) {
                char c = a.charAt(i);
                if (c == '\\' || c == '"') sb.append('\\');
                sb.append(c);
            }
            sb.append('"').append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static boolean hasNativesInfo(Map<String, Object> lib) {
        if (lib == null) return false;
        if (json_obj(lib.get("natives")) != null) return true;
        Map<String, Object> dl = json_obj(lib.get("downloads"));
        return dl != null && json_obj(dl.get("classifiers")) != null;
    }

    // ================================================================ maven 名

    static final class Maven {
        String group, artifact, version, classifier, ext = "jar";

        static Maven parse(String name) {
            if (name == null) return null;
            Maven m = new Maven();
            String n = name;
            int at = n.indexOf('@');
            if (at > 0) {
                m.ext = n.substring(at + 1);
                n = n.substring(0, at);
            }
            String[] p = n.split(":");
            if (p.length < 3) return null;
            m.group = p[0];
            m.artifact = p[1];
            m.version = p[2];
            if (p.length > 3) m.classifier = p[3];
            return m;
        }

        String key() { return group + "|" + artifact + "|" + classifier; }

        String toPath() {
            String base = group.replace('.', '/') + "/" + artifact + "/" + version + "/"
                    + artifact + "-" + version
                    + (classifier == null ? "" : "-" + classifier) + "." + ext;
            return base;
        }
    }

    // ================================================================ json 小工具

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json_obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> json_arr(Object o) {
        return o instanceof List ? (List<Object>) o : List.of();
    }

    private static List<Object> json_arrOrNull(Object o) {
        return o instanceof List ? json_arr(o) : null;
    }

    private static String json_str(Object o) { return o == null ? null : String.valueOf(o); }

    private static double json_num(Object o) {
        return o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o));
    }
}
