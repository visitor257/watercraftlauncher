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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 找 .minecraft 目录。
 * 先给几个「一定对」的（当前运行目录、APPDATA、上次用过的），
 * 再后台扫描磁盘补全，扫到就回调。
 */
public final class Dirs {

    private static final List<Path> found = new CopyOnWriteArrayList<>();
    private static volatile boolean scanned = false;
    private static volatile boolean scanning = false;
    private static volatile boolean seeded = false;

    /**
     * 能切的所有目录：手动登记的 → 上次用过的 → 自动扫到的。
     * 顺序就是「启动」页 ◀ ▶ 的顺序，用户刚加的排最前。
     */
    public static List<Path> known() {
        if (!seeded) seed();
        Set<Path> out = new LinkedHashSet<>();
        for (String s : Config.dirs) {
            try { addIfExists(out, Paths.get(s)); } catch (Exception ignored) { }
        }
        if (Config.lastDir != null && !Config.lastDir.trim().isEmpty()) {
            try { addIfExists(out, Paths.get(Config.lastDir)); } catch (Exception ignored) { }
        }
        out.addAll(found);
        return new ArrayList<>(out);
    }

    /** 登记一个目录（点「添加」或切换时调用）。 */
    public static void add(Path p) {
        add(p, null);
    }

    /**
     * 登记 p，并保证 alsoKeep（切换前的那个）也还在列表里。
     * 不这样的话：当前目录只靠 lastDir 存在，一旦切走就被新值盖掉，
     * 用户看到的就是「点添加把原来那个替换掉了」。
     */
    public static void add(Path p, Path alsoKeep) {
        if (p == null) return;
        Path n = p.toAbsolutePath().normalize();
        Path prev = null;
        if (alsoKeep != null) {
            Path q = alsoKeep.toAbsolutePath().normalize();
            if (!q.equals(n) && Files.isDirectory(q)) prev = q;
        }
        if (prev == null && Config.lastDir != null && !Config.lastDir.trim().isEmpty()) {
            try {
                Path q = Paths.get(Config.lastDir).toAbsolutePath().normalize();
                if (!q.equals(n) && Files.isDirectory(q)) prev = q;
            } catch (Exception ignored) { }
        }
        synchronized (Config.dirs) {
            final Path keep = prev;
            Config.dirs.removeIf(s -> samePath(s, n));
            if (keep != null) Config.dirs.removeIf(s -> samePath(s, keep));
            Config.dirs.add(0, n.toString());
            if (keep != null) Config.dirs.add(1, keep.toString());
        }
        remember(n);
        Config.save();
    }

    /** 只登记到历史里，不改 known() 列表（开机恢复目录时用）。 */
    public static void touchHistory(Path p) {
        if (p == null) return;
        remember(p.toAbsolutePath().normalize());
    }

    /** 从列表里移掉（自动扫到的还会出现，只是不再置顶）。 */
    public static void remove(Path p) {
        if (p == null) return;
        synchronized (Config.dirs) {
            Config.dirs.removeIf(s -> samePath(s, p));
        }
        rewriteHistory(history().stream()
                .filter(x -> !x.toAbsolutePath().normalize().equals(p.toAbsolutePath().normalize()))
                .collect(java.util.stream.Collectors.toList()));
        Config.save();
    }

    private static boolean samePath(String s, Path p) {
        try {
            return Paths.get(s).toAbsolutePath().normalize().equals(p.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isScanning() { return scanning; }

    private static void seed() {
        seeded = true;
        Set<Path> init = new LinkedHashSet<>();

        // 1) 上次用过
        init.addAll(history());

        // 2) 用户目录
        String home = System.getProperty("user.home");
        if (home != null) init.add(Paths.get(home, ".minecraft"));

        // 3) APPDATA / XDG / macOS
        String appdata = System.getenv("APPDATA");
        if (appdata != null) init.add(Paths.get(appdata, ".minecraft"));
        if (home != null) {
            init.add(Paths.get(home, ".local", "share", "minecraft"));
            init.add(Paths.get(home, "Library", "Application Support", "minecraft"));
        }

        // 4) 当前工作目录（mod 里就是跑着的那个 .minecraft）
        init.add(Paths.get("").toAbsolutePath().resolve(".minecraft"));
        init.add(Paths.get("").toAbsolutePath());

        for (Path p : init) {
            if (looksLikeGameDir(p)) addIfNew(p);
        }
    }

    private static void addIfExists(Set<Path> out, Path p) {
        Path n = p.toAbsolutePath().normalize();
        if (Files.isDirectory(n)) out.add(n);
    }

    private static void addIfNew(Path p) {
        Path n = p.toAbsolutePath().normalize();
        for (Path e : found) if (e.equals(n)) return;
        found.add(n);
    }

    /** 后台扫盘。扫到新目录就回调（在扫描线程里调）。 */
    public static void scanAsync(Consumer<Path> onFound) {
        if (scanning || scanned) return;
        scanning = true;
        Thread t = new Thread(() -> {
            try {
                Set<Path> roots = new LinkedHashSet<>();
                String home = System.getProperty("user.home");
                if (home != null) {
                    roots.add(Paths.get(home));
                    roots.add(Paths.get(home, "Desktop"));
                    roots.add(Paths.get(home, "Documents"));
                    roots.add(Paths.get(home, "Downloads"));
                }
                for (String drive : new String[]{"C:", "D:", "E:", "F:", "G:"}) {
                    Path r = Paths.get(drive + "\\");
                    if (Files.isDirectory(r)) roots.add(r);
                }

                int budget = 40000;
                for (Path root : roots) {
                    budget = walk(root, 0, 3, budget, onFound);
                    if (budget <= 0) break;
                }
            } finally {
                scanning = false;
                scanned = true;
            }
        }, "mcver-dirscan");
        t.setDaemon(true);
        t.start();
    }

    private static int walk(Path dir, int depth, int maxDepth, int budget, Consumer<Path> onFound) {
        if (budget <= 0 || depth > maxDepth) return budget;

        // 目录名就叫 .minecraft
        String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
        // 名字叫 .minecraft，或者有 launcher_profiles.json（PCL 用户常把目录改名）
        if (".minecraft".equalsIgnoreCase(name) || Files.isRegularFile(dir.resolve("launcher_profiles.json"))) {
            if (Files.isDirectory(dir.resolve("versions")) || Files.isDirectory(dir.resolve("assets"))
                    || Files.isRegularFile(dir.resolve("launcher_profiles.json"))) {
                boolean isNew = true;
                Path n = dir.toAbsolutePath().normalize();
                for (Path e : found) if (e.equals(n)) { isNew = false; break; }
                addIfNew(dir);
                if (isNew) onFound.accept(dir);
            }
            return budget - 1;
        }

        try (java.nio.file.DirectoryStream<java.nio.file.Path> ds = Files.newDirectoryStream(dir)) {
            for (Path child : ds) {
                if (budget <= 0) break;
                budget--;
                if (!Files.isDirectory(child)) continue;

                String cn = child.getFileName().toString();
                if (skip(cn)) continue;

                budget = walk(child, depth + 1, maxDepth, budget, onFound);
            }
        } catch (Exception ignored) { }
        return budget;
    }

    private static boolean skip(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.equals("windows") || l.equals("$recycle.bin") || l.equals("system volume information")
                || l.equals("node_modules") || l.equals(".git") || l.equals("appdata")
                || l.equals("perflogs") || l.equals("recovery") || l.equals("programdata")
                || l.equals("windows.old") || l.equals("onedrivetemp")
                || l.equals("program files") || l.equals("program files (x86)")
                || l.startsWith("$");
    }

    // ---------------------------------------------------------- 历史

    private static Path historyFile() {
        return Config.dir().resolve("mcver-dirs.txt");
    }

    public static List<Path> history() {
        List<Path> out = new ArrayList<>();
        Path f = historyFile();
        if (!Files.isRegularFile(f)) return out;
        try {
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                if (!line.trim().isEmpty()) out.add(Paths.get(line.trim()));
            }
        } catch (IOException ignored) { }
        return out;
    }

    public static void remember(Path dir) {
        List<Path> list = history();
        list.removeIf(p -> p.toAbsolutePath().normalize().equals(dir.toAbsolutePath().normalize()));
        list.add(0, dir.toAbsolutePath().normalize());
        while (list.size() > 12) list.remove(list.size() - 1);
        rewriteHistory(list);
    }

    private static void rewriteHistory(List<Path> list) {
        Path f = historyFile();
        StringBuilder sb = new StringBuilder();
        for (Path p : list) sb.append(p).append('\n');
        try {
            Files.createDirectories(f.getParent());
            dev.mcver.core.Jdk.writeString(f, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) { }
    }

    /** 判断像不像一个 .minecraft（有 versions 或 assets 或 launcher_profiles.json）。 */
    public static boolean looksLikeGameDir(Path p) {
        return Files.isDirectory(p)
                && (Files.isDirectory(p.resolve("versions"))
                || Files.isDirectory(p.resolve("assets"))
                || Files.isRegularFile(p.resolve("launcher_profiles.json")));
    }

    /** 显示用：末两段路径，足够区分 D:\a\.minecraft 和 D:\b\.minecraft。 */
    public static String shortLabel(Path p) {
        if (p == null) return "";
        Path f = p.getFileName();
        Path parent = p.getParent();
        String leaf = f == null ? p.toString() : f.toString();
        if (parent == null || parent.getFileName() == null) return leaf;
        return parent.getFileName() + "/" + leaf;
    }

    private Dirs() { }
}
