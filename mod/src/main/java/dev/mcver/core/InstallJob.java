package dev.mcver.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 一次安装任务：状态全在这里，UI 只读。
 *
 * 任务跑在自己的 daemon 线程上，关不关界面都不影响它，
 * 界面重新打开时通过 {@link Jobs} 接回来。
 */
public final class InstallJob {

    public enum State { IDLE, RUNNING, DONE, FAILED, CANCELLED }

    public final String versionId;          // MC 原版版本
    public final LoaderInstaller.Kind loader;
    public final String loaderVersion;      // null = 用推荐
    public final LoaderInstaller.Shader shader;
    public final String shaderVersion;      // OptiFine "HD_U/I6"；Iris 版本号
    public final Path gameDir;
    public final boolean isolation;
    public final String targetName;         // 用户起的版本名，null = 用生成的

    public volatile State state = State.IDLE;
    public volatile String stage = "";
    public volatile String detail = "";
    public volatile long done;
    public volatile long total;
    public volatile String error;
    public volatile String resultVersionId;
    public volatile boolean cancelRequested;

    /** 日志限流：下载进度每 10% 才记一条。 */
    private String lastLogStage = "";
    private int lastLogPct = -1;
    private final Deque<String> log = new ArrayDeque<>();
    private static final int LOG_LIMIT = 300;

    public InstallJob(Path gameDir, String versionId, LoaderInstaller.Kind loader, String loaderVersion,
                      LoaderInstaller.Shader shader, String shaderVersion,
                      boolean isolation, String targetName) {
        this.gameDir = gameDir;
        this.versionId = versionId;
        this.loader = loader == null ? LoaderInstaller.Kind.NONE : loader;
        this.loaderVersion = loaderVersion;
        this.shader = shader == null ? LoaderInstaller.Shader.NONE : shader;
        this.shaderVersion = shaderVersion;
        this.isolation = isolation;
        this.targetName = (targetName == null || targetName.isBlank()) ? null : targetName.trim();
    }

    public synchronized List<String> logSnapshot(int max) {
        List<String> all = new ArrayList<>(log);
        int from = Math.max(0, all.size() - max);
        return new ArrayList<>(all.subList(from, all.size()));
    }

    public synchronized void log(String line) {
        log.addLast(line);
        while (log.size() > LOG_LIMIT) log.removeFirst();
        // 同时写文件：界面关掉时还能从外面看进度
        try {
            Path f = gameDir.resolve("mcver-install.log");
            Files.writeString(f,
                    "[" + java.time.LocalTime.now().withNano(0) + "] " + line + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    public double fraction() {
        if (total <= 0) return 0;
        return Math.min(1.0, (double) done / (double) total);
    }

    public void start(java.util.function.Consumer<InstallJob> onFinish) {
        state = State.RUNNING;
        Thread t = new Thread(() -> {
            try {
                run();
                if (cancelRequested) {
                    state = State.CANCELLED;
                } else {
                    state = State.DONE;
                }
            } catch (Throwable e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
                log("!! " + error);
                state = State.FAILED;
            } finally {
                try {
                    onFinish.accept(this);
                } catch (Throwable ignored) { }
            }
        }, "mcver-install");
        t.setDaemon(true);
        t.start();
    }

    private void run() throws Exception {
        Installer.Progress p = new Installer.Progress() {
            @Override
            public void on(String newStage, long d, long t, String msg) {
                if (cancelRequested) throw new RuntimeException("已终止");
                stage = newStage;
                done = d;
                total = t;
                detail = msg == null ? "" : msg;
                noteProgress(newStage, d, t, detail);
            }
        };

        // ---- 1) 原版
        log("开始安装原版 " + versionId);
        stage = "prepare";
        // 记住装之前有没有这个原版：没有的话，装完加载器要把它从列表里藏起来
        // （文件还得留着，加载器版本 inheritsFrom 靠它）
        boolean vanillaExisted = Launcher.isComplete(gameDir, versionId);
        Installer.Installed r;
        Sources.Src src = Sources.active();
        log("下载源: " + src.label());
        try {
            r = Installer.install(gameDir, versionId, src, true, false,
                    Math.max(4, Config.downloadThreads), p);
        } catch (Exception e) {
            if (src.official()) throw e;
            log("下载源失败（" + e.getMessage() + "），改用官方源");
            r = Installer.install(gameDir, versionId, Sources.OFFICIAL, true, false,
                    Math.max(4, Config.downloadThreads), p);
        }
        String result = r.versionId;

        // ---- 2) 模组加载器
        if (loader != LoaderInstaller.Kind.NONE) {
            checkCancel();
            stage = "loader";
            done = 0;
            total = 0;
            detail = "安装 " + loader.label;
            log("---- 安装 " + loader.label + " " + (loaderVersion == null ? "" : loaderVersion) + " ----");

            // 安装器该用哪个 Java：先按「这个 MC 版本自己要求的」来（1.7.10 的 2014 年
            // installer 在 Java 17/21 上会直接崩），失败再退到 17+。
            // 以前这里写死 max(17, requiredJava)，等于强制用最新的 Java 跑老安装器。
            String comp = (String) r.javaComponent;
            int[] candidates = new int[]{Math.max(8, r.requiredJava), Math.max(17, r.requiredJava)};

            String id = null;
            Exception lastErr = null;
            for (int i = 0; i < candidates.length && id == null; i++) {
                int need = candidates[i];
                if (i > 0 && candidates[i] == candidates[i - 1]) continue;
                checkCancel();

                Path java = JavaFinder.forMajor(need);
                if (java == null) {
                    log("本机没有 Java " + need + "，尝试下载官方运行时…");
                    java = JavaRuntime.ensure(gameDir, comp, need, this::log);
                }
                if (java == null) {
                    lastErr = new Exception("找不到 Java " + need + "+，也下载不到官方运行时");
                    continue;
                }
                log("使用 Java: " + java);
                try {
                    id = LoaderInstaller.install(gameDir, loader, loaderVersion, versionId, java, this::log);
                } catch (Exception e) {
                    lastErr = e;
                    if (i + 1 < candidates.length && candidates[i + 1] != need) {
                        log("用 Java " + need + " 装失败（" + e.getMessage() + "），换更高的 Java 重试");
                    }
                }
            }
            if (id == null) {
                throw new Exception("装不了 " + loader.label + "（" + versionId + " 要求 Java "
                        + r.requiredJava + "）：" + (lastErr == null ? "未知原因" : lastErr.getMessage()));
            }
            // 注意：这一行不能漏。当时重写「按版本要求选 Java」的循环（bug #6）把它删了，
            // 后果是 result 停在原版 id 上 → 下一步改名把 **versions/<mc> 原版目录** 改成了目标名，
            // 加载器目录另在一旁，启动验证跑的是原版（日志照样有 Setting user）→ 假通过。
            // 2026-10-26 复现并修正：1.7.10/1.10.2/1.12.2 Forge、26.1 Forge/NeoForge、两个 Iris 组合都中招。
            result = id;
        }

        // ---- 3) 光影加载器
        if (shader == LoaderInstaller.Shader.OPTIFINE) {
            checkCancel();
            stage = "shader";
            done = 0;
            total = 0;
            detail = "安装 OptiFine";
            log("---- 安装 OptiFine ----");
            if (loader != LoaderInstaller.Kind.NONE) {
                log("提示: OptiFine 一般配原版用，加载器版本可能不兼容");
            }
            String id = LoaderInstaller.installOptifine(gameDir, versionId, shaderVersion, this::log);
            if (id != null) result = id;
        }

        // ---- 4) 版本改名
        if (targetName != null && !targetName.equals(result)) {
            stage = "rename";
            detail = "改名 " + targetName;
            String err = VersionMeta.rename(gameDir, result, targetName);
            if (err != null) {
                log("改名失败（保留 " + result + "）: " + err);
            } else {
                log("版本已命名为 " + targetName);
                result = targetName;
            }
        }

        // ---- 5) Iris（mod 形式，装进 mods）
        if (shader == LoaderInstaller.Shader.IRIS) {
            checkCancel();
            stage = "shader";
            done = 0;
            total = 0;
            detail = "安装 Iris";
            log("---- 安装 Iris ----");
            if (loader != LoaderInstaller.Kind.FABRIC) {
                throw new Exception("Iris 需要 Fabric，请把模组加载器选成 Fabric");
            }
            Path mods = isolation
                    ? gameDir.resolve("versions").resolve(result).resolve("mods")
                    : gameDir.resolve("mods");
            Files.createDirectories(mods);
            LoaderInstaller.installIris(mods, versionId, shaderVersion, this::log);
        }

        // ---- 6) 版本隔离
        if (isolation) {
            stage = "isolation";
            done = 0;
            total = 0;
            detail = "开启版本隔离";
            Launcher.setIsolated(gameDir, result, true);
            log("版本隔离已开启（启动时 --gameDir 指向 versions/" + result + "）");
        } else {
            Files.createDirectories(gameDir.resolve("mods"));
            log("版本隔离已关闭，共用根目录 mods");
        }

        resultVersionId = result;
        stage = "done";
        done = 1;
        total = 1;
        detail = "完成";
        Launcher.markComplete(gameDir.resolve("versions").resolve(result));

        // 装模组加载器时附带的原版：如果装之前没有，就把它**合并**进加载器版本，
        // 否则 versions/ 下会多出一个 1.20.1（files 还得留着，但用户看到的应该只有 1.20.1-Fabric）。
        if (loader != LoaderInstaller.Kind.NONE && !vanillaExisted && !result.equals(versionId)) {
            try {
                if (Launcher.flatten(gameDir, result, versionId)) {
                    log("已合并成单一版本：versions/" + result + "（versions/" + versionId + " 已移除）");
                } else {
                    Launcher.setHidden(gameDir, versionId, true);
                    log("原版 " + versionId + " 被别的版本引用，保留文件但隐藏");
                }
            } catch (Exception e) {
                try { Launcher.setHidden(gameDir, versionId, true); } catch (Exception ignored) { }
                log("合并失败（" + e.getMessage() + "），改为隐藏原版");
            }
        }
        log("安装完成: " + result);
    }

    /** 进度条类的刷新只按 10% 记一条，且不用那些没营养的 “123 MB”。 */
    private void noteProgress(String st, long d, long t, String msg) {
        if (msg == null || msg.isBlank()) return;
        if (t <= 0) { log(msg); return; }                     // 非进度型消息：原样记
        int pct = (int) Math.min(100, d * 100 / Math.max(1, t));
        boolean newStage = !st.equals(lastLogStage);
        // 多线程下载时回调会乱序（先报 50% 再报 40%），非递增的一律丢掉
        if (!newStage && pct <= lastLogPct) return;
        if (!newStage) {
            if (pct >= 100) {
                if (lastLogPct >= 100) return;
            } else if (pct < lastLogPct + 10) {
                return;
            }
        }
        lastLogStage = st;
        lastLogPct = pct;
        log(stageName(st) + " " + pct + "%（" + d + "/" + t + "）");
    }

    private static String stageName(String st) {
        return switch (st == null ? "" : st) {
            case "manifest" -> "获取版本清单";
            case "version" -> "下载版本元数据";
            case "plan" -> "解析依赖";
            case "files" -> "下载 jar 与库";
            case "assets" -> "下载资源文件";
            case "loader" -> "安装加载器";
            case "shader" -> "安装光影";
            case "rename" -> "重命名版本";
            case "isolation" -> "开启版本隔离";
            case "done" -> "完成";
            default -> st == null || st.isBlank() ? "准备中" : st;
        };
    }

    private void checkCancel() {
        if (cancelRequested) throw new RuntimeException("已取消");
    }

    public List<String> logList() {
        synchronized (this) { return Collections.unmodifiableList(new ArrayList<>(log)); }
    }
}
