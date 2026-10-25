package dev.mcver.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 找机器上的 Java。
 *
 * 原来的做法是按 major 只留一个，界面上看不到别的；现在改成**每一个都列出来**：
 * 用户手动加的（Config.javaExtra）排最前，然后是当前进程的 Java、JAVA_HOME、
 * 各家的安装目录、PCL 记录、.minecraft/runtime。
 *
 * 版本号靠读 JDK 根目录下的 release 文件，比跑 -version 快得多；
 * 读不到才真去跑一次 java -version。
 */
public final class JavaFinder {

    public record Found(Path exe, int major, String raw, boolean custom, String arch) {
        /** "Java 21 x86_64 · ...\bin\java.exe" */
        public String label() {
            return "Java " + major + (arch == null || arch.isBlank() ? "" : " " + arch) + "  " + exe;
        }
    }

    private static List<Found> cache;

    public static synchronized void invalidate() { cache = null; }

    public static synchronized List<Found> all() {
        if (cache != null) return cache;

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean win = os.contains("win");
        String exe = win ? "java.exe" : "java";

        // 按「用户最想看到的顺序」收集；LinkedHashSet 去重靠路径字符串
        Set<Path> candidates = new LinkedHashSet<>();
        Set<Path> customPaths = new LinkedHashSet<>();

        // 1) 用户自己加进来的
        for (String raw : Config.javaExtra) {
            Path p = resolveExe(raw);
            if (p != null) { candidates.add(p); customPaths.add(p); }
        }

        // 2) 当前进程自己
        ProcessHandle.current().info().command().ifPresent(c -> {
            try { candidates.add(Paths.get(c)); } catch (Exception ignored) { }
        });

        // 3) JAVA_HOME
        String jh = System.getenv("JAVA_HOME");
        if (jh != null && !jh.isBlank()) {
            try { candidates.add(Paths.get(jh, "bin", exe)); } catch (Exception ignored) { }
        }

        // 4) 常见安装目录
        List<String> roots = new ArrayList<>();
        if (win) {
            for (String drive : new String[]{"C:", "D:", "E:", "F:", "G:"}) {
                roots.add(drive + "\\Program Files\\Java\\");
                roots.add(drive + "\\Program Files\\Eclipse Adoptium\\");
                roots.add(drive + "\\Program Files\\BellSoft\\");
                roots.add(drive + "\\Program Files\\Microsoft\\");
                roots.add(drive + "\\Program Files\\Zulu\\");
                roots.add(drive + "\\Program Files\\Amazon Corretto\\");
                roots.add(drive + "\\Program Files\\Semeru\\");
            }
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
                String pf = System.getenv(env);
                if (pf != null) {
                    roots.add(pf + "\\Java\\");
                    roots.add(pf + "\\Eclipse Adoptium\\");
                    roots.add(pf + "\\Microsoft\\");
                    roots.add(pf + "\\Programs\\Eclipse Adoptium\\");
                }
            }
            String home = System.getProperty("user.home");
            if (home != null) roots.add(home + "\\.jdks\\");
        } else {
            roots.add("/usr/lib/jvm/");
            roots.add("/usr/java/");
            roots.add("/opt/java/");
            roots.add("/opt/jdk/");
            roots.add("/Library/Java/JavaVirtualMachines/");
            String home = System.getProperty("user.home");
            if (home != null) {
                roots.add(home + "/.jdks/");
                roots.add(home + "/.sdkman/candidates/java/");
            }
        }

        for (String r : roots) {
            Path root;
            try { root = Paths.get(r); } catch (Exception e) { continue; }
            if (!Files.isDirectory(root)) continue;
            try (var ds = Files.list(root)) {
                ds.filter(Files::isDirectory).forEach(d -> {
                    Path p = d.resolve("bin").resolve(exe);
                    if (Files.isRegularFile(p)) candidates.add(p);
                    // macOS: Foo.jdk/Contents/Home/bin/java
                    Path mac = d.resolve("Contents").resolve("Home").resolve("bin").resolve(exe);
                    if (Files.isRegularFile(mac)) candidates.add(mac);
                });
            } catch (IOException ignored) { }
        }

        // 5) PCL 记录的 JavaList
        String appdata = System.getenv("APPDATA");
        if (appdata != null) {
            Path pcl = Paths.get(appdata, "PCL", "config.json");
            if (Files.isRegularFile(pcl)) {
                try {
                    String txt = Files.readString(pcl, StandardCharsets.UTF_8);
                    for (String s : txt.split("\"Folder\"")) {
                        int q1 = s.indexOf('"');
                        if (q1 < 0) continue;
                        int q2 = s.indexOf('"', q1 + 1);
                        if (q2 < 0) continue;
                        String folder = s.substring(q1 + 1, q2).replace("\\\\", "\\");
                        if (folder.length() > 3) candidates.add(Paths.get(folder, exe));
                    }
                } catch (Exception ignored) { }
            }
        }

        // 6) Mojang 自带 runtime（APPDATA 的 + 当前工作目录的）
        Set<Path> runtimes = new LinkedHashSet<>();
        if (appdata != null) runtimes.add(Paths.get(appdata, ".minecraft", "runtime"));
        String home = System.getProperty("user.home");
        if (home != null) runtimes.add(Paths.get(home, ".minecraft", "runtime"));
        runtimes.add(Paths.get("").toAbsolutePath().resolve("runtime"));
        for (Path rt : runtimes) {
            if (Files.isDirectory(rt)) scanRuntime(rt, exe, candidates);
        }

        List<Found> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) continue;
            String key = p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            Found f = inspect(p);
            if (f == null || f.major() <= 0) continue;
            // 64 位系统上直接把 32 位 JVM 过滤掉：MC 1.13+ 的 natives 全是 64 位，
            // 选到 32 位 java 的表现就是「pid 起来了、但窗口一直出不来」。
            if (!is32bitOs() && "x86".equals(f.arch())) continue;
            out.add(new Found(f.exe(), f.major(), f.raw(), customPaths.contains(p), f.arch()));
        }
        // 版本高的在前；同版本 java.exe 优先于 javaw.exe；最后按路径排
        out.sort((a, b) -> {
            int c = Integer.compare(b.major(), a.major());
            if (c != 0) return c;
            int j = Boolean.compare(isJavaw(b.exe()), isJavaw(a.exe()));
            if (j != 0) return j;
            return a.exe().toString().compareToIgnoreCase(b.exe().toString());
        });
        cache = out;
        return out;
    }

    private static boolean isJavaw(Path p) {
        return p.getFileName() != null
                && "javaw.exe".equalsIgnoreCase(p.getFileName().toString());
    }

    /** 读 JDK 根目录 release 里的 OS_ARCH，判断是不是 32 位 JVM（读不到当 64 位）。 */
    static boolean is32Bit(Path javaExe) {
        return "x86".equals(inspect(javaExe).arch());
    }

    /** 当前系统是不是 32 位（32 位系统上就不该过滤 32 位 JVM）。 */
    static boolean is32bitOs() {
        return "x86".equals(normArch(System.getProperty("os.arch", "")));
    }

    /** 架构名字归一化：amd64/x86_64 → x86_64，i386/i686 → x86，aarch64 → arm64。 */
    static String normArch(String a) {
        String s = a == null ? "" : a.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "amd64", "x86_64", "x86-64" -> "x86_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "aarch64", "arm64" -> "arm64";
            default -> s;
        };
    }

    static void scanRuntime(Path runtime, String exe, Set<Path> out) {
        try (var ds = Files.walk(runtime, 6)) {
            ds.filter(p -> p.getFileName() != null && p.getFileName().toString().equals(exe))
              .filter(Files::isRegularFile)
              .forEach(out::add);
        } catch (IOException ignored) { }
    }

    /**
     * 读一个 java 可执行文件的版本和架构。
     *
     * 优先读 JDK 根目录的 ``release``（快，不起进程）；读不到才真跑一次
     * ``java -XshowSettings:properties -version`` 一次把两个值都抠出来。
     * 架构很重要 —— 64 位系统上选中 32 位 java，MC 会“有 pid 没窗口”。
     */
    static Found inspect(Path javaExe) {
        Path home = javaExe.getParent() != null ? javaExe.getParent().getParent() : null;
        if (home != null) {
            Path release = home.resolve("release");
            if (Files.isRegularFile(release)) {
                try {
                    String version = null, arch = "";
                    for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
                        if (line.startsWith("JAVA_VERSION=")) {
                            version = line.substring("JAVA_VERSION=".length()).replace("\"", "").trim();
                        } else if (line.startsWith("OS_ARCH=")) {
                            arch = normArch(line.substring("OS_ARCH=".length()).replace("\"", "").trim());
                        }
                    }
                    if (version != null) return new Found(javaExe, majorOf(version), version, false, arch);
                } catch (Exception ignored) { }
            }
        }
        Probe r = probe(javaExe);
        return new Found(javaExe, r.major(), "?", false, r.arch());
    }

    static int majorOf(String v) {
        if (v == null || v.isBlank()) return 0;
        if (v.startsWith("1.")) {
            String[] p = v.split("\\.");
            return p.length > 1 ? parseInt(p[1]) : 0;
        }
        String head = v.split("[.\\-+]")[0];
        return parseInt(head);
    }

    static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    /** 兜底用的探测结果。 */
    record Probe(int major, String arch) { }

    /**
     * 兜底：真跑一次 ``java -XshowSettings:properties -version``，
     * 版本从 ``java.version = x`` 抠，架构从 ``os.arch = x`` 抠。
     * 拿的是 **被探测那个 java 自己的** os.arch，所以 32 位 java 会报 x86。
     */
    static Probe probe(Path javaExe) {
        try {
            ProcessBuilder pb = new ProcessBuilder(javaExe.toString(),
                    "-XshowSettings:properties", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(8, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return new Probe(0, "");
            }
            int major = 0;
            String arch = "";
            for (String line : out.split("\\R")) {
                String s = line.trim();
                if (s.startsWith("java.version =")) {
                    major = majorOf(s.substring("java.version =".length()).trim());
                } else if (s.startsWith("os.arch =")) {
                    arch = normArch(s.substring("os.arch =".length()).trim());
                }
            }
            if (major == 0) {
                var m = java.util.regex.Pattern.compile("version \"([^\"]+)\"").matcher(out);
                if (m.find()) major = majorOf(m.group(1));
            }
            return new Probe(major, arch);
        } catch (Exception ignored) { }
        return new Probe(0, "");
    }

    /**
     * 把用户粘进来的东西变成 java 可执行文件：
     * java.exe / javaw.exe / .../bin / .../bin/java / JDK 根目录 都认。
     * Linux / macOS 填 .../bin/java 即可（那两边没有 javaw）。
     */
    public static Path resolveExe(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        boolean win = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] names = win
                ? new String[]{"java.exe", "javaw.exe"}
                : new String[]{"java"};

        Path p;
        try { p = Paths.get(s); } catch (Exception e) { return null; }

        if (Files.isRegularFile(p)) {
            String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
            if (n.equals("java.exe") || n.equals("javaw.exe") || n.equals("java")) return p;
            return null;
        }
        if (!Files.isDirectory(p)) return null;

        // 目录 → bin/java(.exe)
        List<Path> tries = new ArrayList<>();
        if (p.getFileName() != null && p.getFileName().toString().equalsIgnoreCase("bin")) {
            for (String n : names) tries.add(p.resolve(n));
        } else {
            for (String n : names) tries.add(p.resolve("bin").resolve(n));
            // JDK 根目录也可能是 .../Contents/Home（macOS）
            for (String n : names) tries.add(p.resolve("Contents").resolve("Home").resolve("bin").resolve(n));
            // 直接是 .../bin/java 父目录
            for (String n : names) tries.add(p.resolve(n));
        }
        for (Path t : tries) if (Files.isRegularFile(t)) return t;
        return null;
    }

    /**
     * 找一个能满足 major 的 java。
     *
     * 顺序：刚好这一版 → 更高但最接近的一版（列表已按版本降序，所以最后一个匹配就是最接近）
     *       → 一个都不满足就返回 null，让调用方报错。
     * 以前这里是「找不到就返回版本最低的那个」，结果拿 Java 8 去跑 1.21.4，
     * 报出来的错跟“版本不对”毫无关系，很难查。
     */
    public static Path forMajor(int major) {
        List<Found> list = all();
        for (Found f : list) if (f.major() == major) return f.exe();
        Path newer = null;
        for (Found f : list) if (f.major() > major) newer = f.exe();   // 降序 → 最后命中的最接近
        if (newer != null) return newer;
        return null;
    }

    /** 版本最高的那个（兜底用，不保证能满足要求）。 */
    public static Path highest() {
        List<Found> list = all();
        return list.isEmpty() ? null : list.get(0).exe();
    }

    /** 指定路径对应的 major；找不到返回 0。 */
    public static int majorOfExe(Path exe) {
        if (exe == null) return 0;
        for (Found f : all()) {
            if (f.exe().toAbsolutePath().normalize().equals(exe.toAbsolutePath().normalize())) return f.major();
        }
        return inspect(exe).major();
    }

    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Found f : all()) sb.append("Java ").append(f.major()).append(" -> ").append(f.exe()).append('\n');
        return sb.toString();
    }

    private JavaFinder() { }
}
