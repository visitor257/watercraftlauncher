package dev.mcver.ui;

import dev.mcver.McVerMod;
import dev.mcver.core.Account;
import dev.mcver.core.Auth;
import dev.mcver.core.Config;
import dev.mcver.core.Dirs;
import dev.mcver.core.JavaFinder;
import dev.mcver.core.Launcher;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 「启动」页：当前目录（可切换）+ 版本列表 + 内存 + 启动。
 *
 * 目录那一行是 [◀] [D:\a\.minecraft] [▶] [＋]：
 * ◀ ▶ 在已登记/扫到的目录之间翻，点中间那格进目录管理（列表 + 手动粘路径 + 扫盘）。
 */
public class LaunchPage extends Page {

    private enum View { MAIN, DIRS }

    private View view = View.MAIN;

    private final List<String> versions = new ArrayList<>();
    private List<String> unfinished = new ArrayList<>();
    private String selected;
    private int scroll;

    private final List<Path> dirs = new ArrayList<>();
    private int dirScroll;
    private TextFieldWidget dirField;
    private String dirStatus = "";
    private int dirStatusColor = MainScreen.C_DIM;

    private TextFieldWidget memField;
    private int memMb;
    private boolean closeAfter;

    private String status = "";
    private int statusColor = MainScreen.C_DIM;
    private volatile boolean busy;

    private final String autoLaunch = env("MCVER_AUTO_LAUNCH");
    private boolean autoTried;
    private boolean dirsOpened;
    private boolean dirAddTried;

    public LaunchPage(MainScreen host) {
        super(host);
        this.selected = env("MCVER_PICK");
    }

    @Override
    public String nav() { return "启动"; }

    // ================================================================ 几何

    private int dirRowY() { return host.contentY() + 1; }
    private int listTop() { return host.contentY() + 22; }
    private int row2Y() { return host.contentBottom() - 18; }
    private int row1Y() { return row2Y() - 20; }
    private int statusY() { return row1Y() - 12; }
    private int rowsVisible() {
        return Math.max(1, (statusY() - 2 - listTop()) / 18);
    }

    private int dirsTop() { return host.contentY() + 22; }
    private int dirsBottom() { return host.contentBottom() - 32; }
    private int dirRowsVisible() {
        return Math.max(1, (dirsBottom() - 2 - dirsTop()) / 18);
    }

    // ================================================================ init

    @Override
    public void init() {
        if (env("MCVER_DIRS") != null && !dirsOpened) {
            dirsOpened = true;
            view = View.DIRS;
        }
        // 自检钩子：自动粘一个路径并点「添加」（它自己会 rebuild，所以这里直接 return）
        String autoAdd = env("MCVER_DIR_ADD");
        if (autoAdd != null && !dirAddTried) {
            dirAddTried = true;
            view = View.DIRS;
            addDirPath(autoAdd);
            return;
        }
        if (view == View.DIRS) { initDirs(); return; }

        memMb = Config.memMb;
        closeAfter = Config.closeAfterLaunch;
        refreshDirs();
        refresh();

        int x = host.contentX();
        int w = host.contentW();

        // ---- 目录行 [◀][名字][▶][＋]
        add(Btn.builder(Txt.of("◀"), b -> stepDir(-1))
                .dimensions(x, dirRowY(), 16, 16).build());
        String label = (dirs.size() > 1 ? "" : "") + Dirs.shortLabel(UiState.dir());
        add(Btn.builder(Txt.of(trim(label, w - 60)), b -> {
            view = View.DIRS;
            dirStatus = "";
            host.rebuild();
        }).dimensions(x + 18, dirRowY(), w - 56, 16).build());
        add(Btn.builder(Txt.of("▶"), b -> stepDir(1))
                .dimensions(x + w - 38, dirRowY(), 16, 16).build());
        add(Btn.builder(Txt.of("＋"), b -> {
            view = View.DIRS;
            dirStatus = "";
            host.rebuild();
        }).dimensions(x + w - 20, dirRowY(), 20, 16).build());

        // ---- 版本行
        int top = listTop();
        int vis = rowsVisible();
        int maxScroll = Math.max(0, versions.size() - vis);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;
        int from = scroll;
        int to = Math.min(versions.size(), from + vis);
        int ry = top;
        for (int i = from; i < to; i++) {
            String id = versions.get(i);
            boolean sel = id.equals(selected);
            String rowLabel = (sel ? "▶ " : "   ") + id;
            add(Btn.builder(Txt.of(trim(rowLabel, w - 8)), b -> {
                selected = id;
                status = "";
                host.rebuild();
            }).dimensions(x, ry, w, 16).build());
            ry += 18;
        }

        if (versions.size() > vis) {
            scrollHint = (scroll + 1) + "-" + to + " / " + versions.size() + "  (滚轮翻)";
        } else {
            scrollHint = null;
        }
        // 列表下方还有空位就把「没装完的」提示写在那儿，不挡版本行
        unfinishedY = (ry + 12 <= statusY()) ? ry + 2 : -1;

        // ---- 第一行：内存 + 启动后行为
        memField = new TextFieldWidget(this.tr(), x + 24, row1Y(), 44, 16, Txt.of("内存"));
        memField.setMaxLength(5);
        memField.setText(String.valueOf(memMb));
        memField.setChangedListener(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v >= 256 && v <= 65536 && v != Config.memMb) {
                    memMb = v;
                    Config.memMb = v;
                    // 以前只改内存里的 Config，不落盘 → 重启就回去了，也和设置页对不上
                    Config.save();
                }
            } catch (NumberFormatException ignored) { }
        });
        add(memField);

        add(Btn.builder(Txt.of(closeAfter ? "启动后关当前 ✓" : "启动后关当前"),
                b -> {
                    closeAfter = !closeAfter;
                    Config.closeAfterLaunch = closeAfter;
                    Config.save();
                    b.setMessage(Txt.of(closeAfter ? "启动后关当前 ✓" : "启动后关当前"));
                }).dimensions(x + w - 100, row1Y(), 100, 16).build());

        // ---- 第二行：动作
        add(Btn.builder(Txt.of("启动"), b -> doLaunch())
                .dimensions(x, row2Y(), 72, 18).build());
        add(Btn.builder(Txt.of("下载版本"), b -> openWizard())
                .dimensions(x + 76, row2Y(), 80, 18).build());
        add(Btn.builder(Txt.of("刷新"), b -> {
            refreshDirs();
            refresh();
            host.rebuild();
        }).dimensions(x + 160, row2Y(), 44, 18).build());
        add(Btn.builder(Txt.of("关闭"), b -> host.closeSelf())
                .dimensions(x + w - 52, row2Y(), 52, 18).build());

        if (autoLaunch != null && !autoTried) {
            autoTried = true;
            McVerMod.LOGGER.info("mcver: 自动启动 {}, 列表={}", autoLaunch, versions);
            if (autoLaunch.equals("*") || versions.contains(autoLaunch)) {
                if (!"*".equals(autoLaunch)) selected = autoLaunch;
                new Thread(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
                    MinecraftClient.getInstance().execute(this::doLaunch);
                }, "mcver-autolaunch").start();
            }
        }
    }

    private String scrollHint;
    private int unfinishedY = -1;
    private String lastSig;

    private void refresh() {
        List<String> list = Launcher.installed(UiState.dir());
        unfinished = Launcher.unfinished(UiState.dir());
        versions.clear();
        versions.addAll(list);
        if (selected == null && !versions.isEmpty()) selected = versions.get(0);
        if (selected != null && !versions.contains(selected)) {
            selected = versions.isEmpty() ? null : versions.get(0);
        }
        // 只在列表真变了才写日志（roll 一下滚轮不该刷屏）
        String sig = versions + "|" + unfinished;
        if (!sig.equals(lastSig)) {
            lastSig = sig;
            McVerMod.LOGGER.info("mcver: 可启动版本 {}（隐藏未完成 {}）", versions, unfinished);
        }
    }

    private void refreshDirs() {
        dirs.clear();
        dirs.addAll(dirList());
        dirScroll = Math.max(0, Math.min(dirScroll, Math.max(0, dirs.size() - dirRowsVisible())));
    }

    /** 已经知道的目录；当前目录永远在第一格。 */
    private static List<Path> dirList() {
        List<Path> out = new ArrayList<>();
        Path cur = UiState.dir();
        out.add(cur);
        for (Path p : Dirs.known()) {
            if (!p.equals(cur) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    private void stepDir(int delta) {
        List<Path> list = dirList();
        if (list.size() <= 1) {
            dirStatus = "只有这一个目录，点「＋」加别的";
            view = View.DIRS;
            host.rebuild();
            return;
        }
        int idx = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).equals(UiState.dir())) { idx = i; break; }
        withDir(list.get(Math.floorMod(idx + delta, list.size())));
    }

    private void withDir(Path p) {
        UiState.switchDir(p);
        scroll = 0;
        selected = null;
        status = "";
        refreshDirs();
        refresh();
        host.rebuild();
    }

    private void openWizard() {
        host.switchTo("下载");
    }

    // ---------------------------------------------------------------- 目录管理视图

    private void initDirs() {
        int x = host.contentX();
        int w = host.contentW();

        add(Btn.builder(Txt.of("◀ 返回"), b -> {
            view = View.MAIN;
            host.rebuild();
        }).dimensions(x, host.contentY() + 1, 46, 16).build());

        add(Btn.builder(Txt.of(Dirs.isScanning() ? "扫描中…" : "扫描磁盘"), b -> {
            Dirs.scanAsync(p -> MinecraftClient.getInstance().execute(() -> {
                if (view == View.DIRS) {
                    refreshDirs();
                    host.rebuild();
                }
            }));
            host.rebuild();
        }).dimensions(x + w - 60, host.contentY() + 1, 60, 16).build());

        refreshDirs();
        McVerMod.LOGGER.info("mcver: 目录列表 {} 当前={}", dirs, UiState.dir());
        int vis = dirRowsVisible();
        int from = Math.max(0, Math.min(dirScroll, Math.max(0, dirs.size() - vis)));
        int ry = dirsTop();
        for (int i = from; i < Math.min(dirs.size(), from + vis); i++) {
            Path p = dirs.get(i);
            boolean sel = p.equals(UiState.dir());
            boolean pinned = isPinned(p);
            final int rowW = pinned ? w - 16 : w;
            add(Btn.builder(Txt.of(trim((sel ? "▶ " : "   ") + p, rowW - 8)), b -> {
                withDir(p);
                view = View.MAIN;
                dirStatus = "已切换到 " + p;
                dirStatusColor = MainScreen.C_OK;
                host.rebuild();
            }).dimensions(x, ry, rowW, 16).build());
            if (pinned) {
                add(Btn.builder(Txt.of("✕"), b -> {
                    if (p.equals(UiState.dir())) {
                        dirStatus = "当前目录不能移出列表";
                        dirStatusColor = MainScreen.C_ERR;
                    } else {
                        Dirs.remove(p);
                        dirStatus = "已移出列表（自动扫到的目录下次扫描还会回来）";
                        dirStatusColor = MainScreen.C_DIM;
                    }
                    refreshDirs();
                    host.rebuild();
                }).dimensions(x + w - 14, ry, 14, 16).build());
            }
            ry += 18;
        }

        int y = host.contentBottom() - 18;
        dirField = new TextFieldWidget(this.tr(), x, y, w - 66, 18, Txt.of("目录路径"));
        dirField.setMaxLength(260);
        dirField.setText("");
        add(dirField);

        add(Btn.builder(Txt.of("添加"), b -> addDirFromField())
                .dimensions(x + w - 62, y, 62, 18).build());
    }

    private boolean isPinned(Path p) {
        for (String s : Config.dirs) {
            try {
                if (Paths.get(s).toAbsolutePath().normalize().equals(p)) return true;
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void addDirFromField() {
        addDirPath(dirField == null ? "" : dirField.getText().trim());
    }

    private void addDirPath(String raw0) {
        String raw = raw0 == null ? "" : raw0.trim();
        if (raw.isEmpty()) {
            dirStatus = "先把 .minecraft 路径粘进来";
            dirStatusColor = MainScreen.C_ERR;
            host.rebuild();
            return;
        }
        try {
            if (raw.startsWith("~")) {
                raw = System.getProperty("user.home", "") + raw.substring(1);
            }
            Path p = Paths.get(raw).toAbsolutePath().normalize();
            if (Files.exists(p) && !Files.isDirectory(p)) {
                dirStatus = "这是个文件，不是目录";
                dirStatusColor = MainScreen.C_ERR;
                host.rebuild();
                return;
            }
            Files.createDirectories(p);
            dirStatus = Dirs.looksLikeGameDir(p)
                    ? "已添加并切到 " + Dirs.shortLabel(p) + "（原来的目录还在下面）"
                    : "已添加并切过去；目录里还没有 versions/assets（下载时会建）";
            dirStatusColor = MainScreen.C_OK;
            view = View.DIRS;
            withDir(p);
        } catch (Exception e) {
            dirStatus = "路径不对: " + e.getMessage();
            dirStatusColor = MainScreen.C_ERR;
            host.rebuild();
        }
    }

    // ================================================================ 启动

    /**
     * 「pid 有、但窗口出不来」的看门狗。
     *
     * 25 秒后如果子进程还活着、日志里却连 Backend library / Setting user 都没有，
     * 就说明卡在开窗口之前（native 崩了 / 选错 Java / GL 起不来），
     * 把判据和日志路径直接摆到界面上，省得用户只能看到一句 pid。
     */
    private void watchWindow(Launcher.Running r) {
        Thread t = new Thread(() -> {
            try { Thread.sleep(25_000); } catch (InterruptedException e) { return; }
            if (!r.process.isAlive()) return;
            boolean windowed = false;
            try {
                for (String line : Files.readAllLines(r.logFile)) {
                    if (line.contains("Backend library:") || line.contains("Setting user:")) {
                        windowed = true;
                        break;
                    }
                }
            } catch (Exception ignored) { }
            if (windowed) return;

            // JVM 级崩溃会留下 hs_err_pid<pid>.log，那个才是一手证据
            String hs = "";
            try {
                Path wd = r.logFile.getParent() == null ? null : r.logFile.getParent().getParent();
                if (wd != null) {
                    Path err = wd.resolve("hs_err_pid" + r.process.pid() + ".log");
                    if (Files.isRegularFile(err)) hs = "（JVM 崩溃报告: " + err + "）";
                }
            } catch (Exception ignored) { }

            final String hint = hs.isEmpty()
                    ? "pid " + r.process.pid() + " 还活着，但 25 秒没窗口（日志没到 Backend library）。看: " + r.logFile
                    : "pid " + r.process.pid() + " 卡在开窗口，JVM 崩了" + hs;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                status = hint;
                statusColor = MainScreen.C_ERR;
            });
        }, "mcver-watch");
        t.setDaemon(true);
        t.start();
    }

    private void doLaunch() {
        if (busy) return;
        if (selected == null) {
            status = "先选一个版本";
            statusColor = MainScreen.C_ERR;
            McVerMod.LOGGER.warn("mcver: 启动被跳过，没选中版本");
            return;
        }
        final String id = selected;
        final Path gameDir = UiState.dir();
        final int mem = memMb;
        final boolean closeAfterThis = closeAfter;

        busy = true;
        status = "正在准备 " + id + " …";
        statusColor = MainScreen.C_DIM;

        // 注意：Session 的包名跨版本变过（1.20.1 是 client.util，1.21 起是 client.session），
        // 所以这里只写 var，不 import 具体类；用到的三个方法在两个版本里签名一致。
        var s = MinecraftClient.getInstance().getSession();
        Account acc = Config.account();
        final String player;
        final String uuid;
        final String token;
        if (acc != null) {
            player = acc.name;
            uuid = acc.uuid;
            token = acc.accessToken.isEmpty() ? "0" : acc.accessToken;
        } else {
            player = s.getUsername();
            uuid = dev.mcver.core.Compat.sessionUuid() == null ? "0" : dev.mcver.core.Compat.sessionUuid();
            token = s.getAccessToken();
        }

        Thread t = new Thread(() -> {
            String msg;
            int color;
            boolean ok = false;
            Launcher.Running running = null;
            try {
                // 正版 token 过期就顺手续一次
                if (acc != null && acc.isMsa() && acc.expired() && !acc.refreshToken.isEmpty()) {
                    try {
                        Account fresh = Auth.refresh(acc);
                        acc.accessToken = fresh.accessToken;
                        acc.refreshToken = fresh.refreshToken;
                        acc.expiresAt = fresh.expiresAt;
                        Config.save();
                    } catch (Exception e) {
                        McVerMod.LOGGER.warn("mcver: token 续期失败 {}", e.getMessage());
                    }
                }

                Launcher.Plan plan = Launcher.plan(gameDir, id);
                McVerMod.LOGGER.info("mcver: 启动计划 runDir={} 隔离={}", plan.runDir,
                        Launcher.isIsolated(gameDir, id));
                if (!Config.jvmArgs.isBlank()) plan.jvm.addAll(Config.splitArgs(Config.jvmArgs));
                if (!Config.gameArgs.isBlank()) plan.game.addAll(Config.splitArgs(Config.gameArgs));
                if (Config.fullscreen) {
                    plan.game.add("--fullscreen");
                } else {
                    plan.game.add("--width");
                    plan.game.add(String.valueOf(Config.winWidth));
                    plan.game.add("--height");
                    plan.game.add(String.valueOf(Config.winHeight));
                }

                if (!plan.ready()) {
                    msg = "启动计划不完整: " + String.join("; ", plan.warnings);
                    color = MainScreen.C_ERR;
                    McVerMod.LOGGER.warn("mcver: plan 不完整 {}", plan.warnings);
                } else if (!plan.missing.isEmpty()) {
                    msg = "缺 " + plan.missing.size() + " 个库文件，例如 " + plan.missing.get(0).getFileName()
                            + "（去「版本」页点补全）";
                    color = MainScreen.C_ERR;
                    McVerMod.LOGGER.warn("mcver: 缺库 {} 例如 {}", plan.missing.size(), plan.missing.get(0));
                } else if (plan.assetMissing != 0) {
                    msg = plan.assetMissing < 0
                            ? "缺资源索引，先用「下载版本」重装一遍"
                            : "缺 " + plan.assetMissing + " 个资源文件，去「版本」页点补全";
                    color = MainScreen.C_ERR;
                    McVerMod.LOGGER.warn("mcver: 资源缺失 {}", plan.assetMissing);
                } else {
                    Launcher.Running r = Launcher.launch(plan, player, uuid, token, mem);
                    running = r;
                    String note = r.javaNote == null || r.javaNote.isBlank() ? "" : "  (" + r.javaNote + ")";
                    msg = "已启动 " + id + "  pid=" + r.process.pid() + note;
                    color = MainScreen.C_OK;
                    ok = true;
                    McVerMod.LOGGER.info("mcver: 已启动 {}  java={}  log={}", id, r.java, r.logFile);
                    watchWindow(r);
                }
            } catch (Throwable e) {
                McVerMod.LOGGER.error("mcver: 启动失败", e);
                msg = "启动失败: " + e.getMessage();
                color = MainScreen.C_ERR;
            }
            final String fmsg = msg;
            final int fcolor = color;
            final boolean fok = ok;
            final Launcher.Running frun = running;
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                busy = false;
                status = fmsg;
                statusColor = fcolor;
                if (fok && closeAfterThis && frun != null) {
                    // 先等几秒确认真活下来了，再关当前窗口；要是它退了就别关，把日志路径摆出来
                    new Thread(() -> {
                        try { Thread.sleep(6000); } catch (InterruptedException ignored) { }
                        if (frun.process.isAlive()) {
                            mc.execute(mc::scheduleStop);
                        } else {
                            mc.execute(() -> {
                                status = "子进程已退出，日志: " + frun.logFile;
                                statusColor = MainScreen.C_ERR;
                            });
                        }
                    }, "mcver-exit").start();
                }
            });
        }, "mcver-launch");
        t.setDaemon(true);
        t.start();
    }

    // ================================================================ render

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {
        if (view == View.DIRS) { renderDirs(ctx); return; }
        renderMain(ctx);
    }

    private void renderMain(Ctx ctx) {
        int x = host.contentX();
        int w = host.contentW();

        if (versions.isEmpty()) {
            text(ctx, "这个目录下还没有装好的版本", x, listTop() + 4, MainScreen.C_DIM);
            text(ctx, "点下面「下载版本」装一个", x, listTop() + 16, MainScreen.C_FAINT);
        }
        if (!unfinished.isEmpty()) {
            // 没装完的版本不进列表（否则点了也启动不了）
            String msg = "另有 " + unfinished.size() + " 个没装完的，已隐藏（去「版本」页删除或补全）";
            if (unfinishedY > 0) {
                text(ctx, trim(msg, w), x, unfinishedY, MainScreen.C_FAINT);
            } else if (scrollHint == null) {
                rightAligned(ctx, trim(msg, w - 60), x + w, statusY(), MainScreen.C_FAINT);
            }
        }

        text(ctx, "内存", x, row1Y() + 4, MainScreen.C_DIM);
        text(ctx, "MB", x + 70, row1Y() + 4, MainScreen.C_FAINT);

        String java = javaSummary();
        if (!java.isEmpty()) {
            text(ctx, java, x + 92, row1Y() + 4, MainScreen.C_FAINT);
        }

        if (!status.isEmpty()) {
            text(ctx, trim(status, w - 92), x, statusY(), statusColor);
        } else if (busy) {
            text(ctx, "正在启动…", x, statusY(), MainScreen.C_WARN);
        }
        if (scrollHint != null) {
            rightAligned(ctx, scrollHint, x + w, statusY(), MainScreen.C_FAINT);
        }
    }

    private static String javaSummary() {
        String custom = Config.javaPath;
        Path p = JavaFinder.resolveExe(custom);
        if (p != null) return "Java " + JavaFinder.majorOfExe(p);
        return "";
    }

    private void renderDirs(Ctx ctx) {
        int x = host.contentX();
        int w = host.contentW();

        text(ctx, "游戏目录", x + 50, host.contentY() + 5, MainScreen.C_TEXT);
        rightAligned(ctx, dirs.size() + " 个", x + w - 64, host.contentY() + 5, MainScreen.C_FAINT);

        if (dirs.isEmpty()) {
            text(ctx, "还没有可用的目录", x, dirsTop() + 4, MainScreen.C_DIM);
        }
        if (Dirs.isScanning()) {
            rightAligned(ctx, "扫描中…", x + w - 64, host.contentY() + 5, MainScreen.C_WARN);
        }
        if (!dirStatus.isEmpty()) {
            text(ctx, trim(dirStatus, w), x, host.contentBottom() - 30, dirStatusColor);
        } else {
            text(ctx, trim("点一行就切过去；也可以在下面粘路径新建", w), x, host.contentBottom() - 30,
                    MainScreen.C_FAINT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int d = (int) Math.signum(vertical);
        if (d == 0) return false;
        if (view == View.DIRS) {
            int vis = dirRowsVisible();
            if (dirs.size() <= vis) return false;
            dirScroll = Math.max(0, Math.min(dirs.size() - vis, dirScroll - d));
            host.rebuild();
            return true;
        }
        int vis = rowsVisible();
        if (versions.size() <= vis) return false;
        scroll = Math.max(0, Math.min(versions.size() - vis, scroll - d));
        host.rebuild();
        return true;
    }
}
