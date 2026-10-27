package dev.mcver.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Mojang 官方 Java 运行时的下载与安装（就是官方启动器「自动下载 Java」那一套）。
 *
 * <p>为什么必须有这个：每个 MC 版本自己声明要哪个 Java（{@code javaVersion.component}），
 * 1.20.5~1.21.x 要 21，26.1 起要 <b>25</b>。用户机器上不会有全部版本，
 * 光靠 {@link JavaFinder} 扫本地，新版本就会直接卡在「找不到 Java」——
 * 这不是「不支持」，是缺运行时。官方启动器就是这么解决的，我们也照做。
 *
 * <p>数据来源（两跳）：
 * <pre>
 *   1) all.json   →  按 [平台][组件名] 找到该 Java 的 manifest 地址
 *   2) manifest   →  files 表，逐文件下载到 runtime/&lt;组件&gt;/&lt;平台&gt;/
 * </pre>
 *
 * <p>装好的目录正好在 {@link JavaFinder} 的扫描路径里（{@code <gameDir>/runtime}），
 * 所以装完 {@code invalidate()} 一下就能被自动选中。
 */
public final class JavaRuntime {

    /** Mojang Java 运行时的总索引（官方启动器用的同一个地址）。 */
    private static final String ALL_JSON =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    /** 已装好的运行时根：<gameDir>/runtime/<component>/<platform>/ */
    public static Path baseDir(Path gameDir) {
        return gameDir.resolve("runtime");
    }

    /**
     * 拿一个满足 {@code needMajor} 的 Java；本机已有就直接返回，没有就下官方运行时。
     *
     * @param gameDir  游戏目录（runtime 装在这下面，跟官方启动器一致）
     * @param component 版本 json 里的 {@code javaVersion.component}，可为 null
     * @param needMajor 最低主版本号
     * @return java(.exe) 的路径；实在拿不到返回 null
     */
    public static Path ensure(Path gameDir, String component, int needMajor, Consumer<String> log) {
        Consumer<String> out = log == null ? s -> { } : log;

        // 1) 本地已有能用的（包括上次下好的 Mojang 运行时）
        Path local = JavaFinder.forMajor(needMajor);
        if (local != null) return local;

        // 2) 没有 → 下官方运行时
        String comp = (component == null || component.trim().isEmpty()) ? guessComponent(needMajor) : component;
        try {
            Path exe = install(gameDir, comp, needMajor, out);
            if (exe != null) {
                JavaFinder.invalidate();
                return exe;
            }
        } catch (Exception e) {
            out.accept("下载 Java " + needMajor + " 运行时失败：" + e.getMessage());
        }
        JavaFinder.invalidate();
        return null;
    }

    /** 只有 component 缺失时才会用到；老版本 manifest 不给这个字段。 */
    static String guessComponent(int major) {
        switch (major) {
            case 8: return "jre-legacy";
            case 16: return "java-runtime-alpha";
            case 17: return "java-runtime-beta";
            case 21: return "java-runtime-delta";
            case 25: return "java-runtime-epsilon";
            default: return "java-runtime-gamma";
        }
    }

    /** 平台键：跟 all.json 里的键名一致。 */
    static String platformKey() {
        String os = Installer.OS_NAME;
        String arch = JavaFinder.normArch(System.getProperty("os.arch", ""));
        if ("windows".equals(os)) {
            if ("arm64".equals(arch)) return "windows-arm64";
            if ("x86".equals(arch)) return "windows-x86";
            return "windows-x64";
        }
        if ("osx".equals(os) || "macos".equals(os)) {
            return "arm64".equals(arch) ? "mac-os-arm64" : "mac-os";
        }
        return "x86".equals(arch) ? "linux-i386" : "linux";
    }

    static String exeName() {
        return "windows".equals(Installer.OS_NAME) ? "java.exe" : "java";
    }

    /** 返回装好的 java 可执行文件；component 在该平台上不存在时返回 null。 */
    static Path install(Path gameDir, String component, int needMajor, Consumer<String> log) throws Exception {
        String plat = platformKey();
        Path root = baseDir(gameDir).resolve(component).resolve(plat);

        // 已经装过（上次成功过）就直接用
        Path ready = root.resolve("bin").resolve(exeName());
        if (Files.isRegularFile(ready)) {
            int m = JavaFinder.inspect(ready).major();
            if (m >= needMajor) {
                log.accept("复用已下载的 Java " + m + "：" + ready);
                return ready;
            }
        }

        log.accept("本机没有 Java " + needMajor + "，下载官方运行时 " + component + "（" + plat + "）…");

        Map<String, Object> all = Json.obj(Json.parse(
                Net.getMirrored(ALL_JSON, null, "Java 运行时索引")));
        Map<String, Object> byPlat = Json.obj(all.get(plat));
        if (byPlat == null) throw new IOException("索引里没有平台 " + plat);

        Object compNode = byPlat.get(component);
        if (compNode == null) throw new IOException("平台 " + plat + " 没有 " + component);

        // 结构是 [ 平台 ][ 组件 ] = [ { manifest:{url}, version:{name} }, ... ]
        List<Object> versions = null;
        if (compNode instanceof List<?>) {
            versions = Json.arr(compNode);
        } else {
            Map<String, Object> m = Json.obj(compNode);
            if (m != null && !m.isEmpty()) versions = Json.arr(m.values().iterator().next());
        }
        if (versions == null || versions.isEmpty()) throw new IOException(component + " 没有可用版本");

        Map<String, Object> first = Json.obj(versions.get(0));
        Map<String, Object> manifestRef = Json.obj(first.get("manifest"));
        String manifestUrl = Json.str(manifestRef, "url", null);
        if (manifestUrl == null) throw new IOException("拿不到 " + component + " 的 manifest 地址");

        Map<String, Object> manifest = Json.obj(Json.parse(
                Net.getMirrored(manifestUrl, null, "Java 运行时清单")));
        Map<String, Object> files = Json.obj(manifest.get("files"));
        if (files == null) throw new IOException("manifest 里没有 files");

        // 只认 type=file 的，先把目录建好、下载任务列出来
        // 局部类（原 record 就写在方法里面）——局部类不能带 static，Java 8 也一样
        final class Job {

        // —— 原来是 record，为了 Java 8 兼容改写成普通类；字段名/方法名跟 record 一致，
        //    所以 found.exe 和 found.exe() 两种写法都还能用。
        public final String url;
        public final Path dst;
        public final boolean exec;

        public Job(String url, Path dst, boolean exec) {
            this.url = url;
            this.dst = dst;
            this.exec = exec;
        }

        public String url() { return url; }
        public Path dst() { return dst; }
        public boolean exec() { return exec; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Job)) return false;
            Job that = (Job) o;
            return true
                && java.util.Objects.equals(url, that.url)
                && java.util.Objects.equals(dst, that.dst)
                && exec == that.exec;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(
                java.util.Objects.hashCode(url),
                java.util.Objects.hashCode(dst),
                exec);
        }

        @Override
        public String toString() {
            return "Job[" + "url=" + url + ", " + "dst=" + dst + ", " + "exec=" + exec + "]";
        }
    }
        List<Job> jobs = new ArrayList<>();
        for (Map.Entry<String, Object> e : files.entrySet()) {
            String rel = e.getKey();
            Map<String, Object> f = Json.obj(e.getValue());
            if (f == null) continue;
            String type = Json.str(f, "type", "file");
            Path dst = root.resolve(rel.replace('\\', '/'));
            if ("directory".equals(type)) {
                Files.createDirectories(dst);
                continue;
            }
            if (!"file".equals(type)) continue;           // link 之类在 Windows 上少见，跳过
            Map<String, Object> dl = Json.obj(f.get("downloads"));
            Map<String, Object> raw = dl == null ? null : Json.obj(dl.get("raw"));
            String url = raw == null ? null : Json.str(raw, "url", null);
            if (url == null) continue;
            jobs.add(new Job(url, dst, Json.bool(f, "executable", false)));
        }
        if (jobs.isEmpty()) throw new IOException("manifest 里没有可下载的文件");

        int total = jobs.size();
        int[] done = {0};
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(4, Math.min(8, Config.downloadThreads)));
        List<Future<?>> futures = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (Job j : jobs) {
            futures.add(pool.submit(() -> {
                try {
                    Net.download(j.url(), j.dst(), 15_000, 120_000);
                    if (j.exec()) {
                        try {
                            j.dst().toFile().setExecutable(true, false);
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ex) {
                    synchronized (errors) {
                        errors.add(j.dst().getFileName() + ": " + ex.getMessage());
                    }
                } finally {
                    int n;
                    synchronized (done) { n = ++done[0]; }
                    if (n % 20 == 0 || n == total) {
                        log.accept("Java 运行时 " + n + "/" + total);
                    }
                }
            }));
        }
        pool.shutdown();
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) { }
        }

        if (!Files.isRegularFile(ready)) {
            String why = errors.isEmpty() ? "没下到 java 可执行文件" : errors.get(0);
            throw new IOException(why + (errors.size() > 1 ? "（共 " + errors.size() + " 个文件失败）" : ""));
        }
        int major = JavaFinder.inspect(ready).major();
        log.accept("Java 运行时 " + component + " 就绪：Java " + major + " → " + ready);
        return ready;
    }

    /** 界面上「哪些 Java 是启动器自己下的」用：runtime 下所有 java 可执行文件。 */
    public static List<Path> installed(Path gameDir) {
        List<Path> out = new ArrayList<>();
        Path rt = baseDir(gameDir);
        if (!Files.isDirectory(rt)) return out;
        try (java.util.stream.Stream<java.nio.file.Path> w = Files.walk(rt, 8)) {
            String exe = exeName();
            w.filter(p -> p.getFileName() != null && p.getFileName().toString().equals(exe))
             .filter(Files::isRegularFile)
             .forEach(out::add);
        } catch (IOException ignored) { }
        return out;
    }

    /**
     * 版本 json 里 {@code javaVersion} 的组件名 + 主版本。
     * 单独拎出来是因为安装和启动两条链路都要读它。
     */
    public static Map<String, Object> describe(Map<String, Object> versionJson) {
        Map<String, Object> jv = Json.obj(versionJson.get("javaVersion"));
        if (jv == null) return new LinkedHashMap<>();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("component", Json.str(jv, "component", null));
        out.put("major", Json.num(jv, "majorVersion", 8));
        return out;
    }

    private JavaRuntime() { }

    static String normLower(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
