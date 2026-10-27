package dev.mcver.ui;

import dev.mcver.McVerMod;
import dev.mcver.core.Config;
import dev.mcver.core.Dirs;
import dev.mcver.core.Sources;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** 「设置」页：内存 / JVM / 游戏参数 / 窗口 / 下载线程 / 下载源 / Java / 版本隔离。 */
public class SettingsPage extends Page {

    private enum View { MAIN, SOURCES }

    private View view = View.MAIN;

    private TextFieldWidget memField, threadField, jvmField, gameField, wField, hField, dirField;
    private TextFieldWidget srcNameField, srcUrlField;
    private boolean fullscreen;
    private boolean isolation;

    private List<Sources.Src> sources = dev.mcver.core.Jdk.emptyList();
    private int srcScroll;
    private boolean srcHookTried;

    private String status = "";

    public SettingsPage(MainScreen host) {
        super(host);
    }

    @Override
    public String nav() { return "设置"; }

    private int rowY(int i) { return host.contentY() + 2 + i * 22; }

    // 下载源视图的几何
    private int srcTop() { return host.contentY() + 22; }
    private int srcBottom() { return host.contentBottom() - 42; }
    private int srcRows() { return Math.max(1, (srcBottom() - srcTop()) / 18); }

    // ================================================================ init

    @Override
    public void init() {
        if (envSrc("MCVER_SOURCES") != null && view == View.MAIN) view = View.SOURCES;
        if (view == View.SOURCES) { initSources(); return; }

        int x = host.contentX();
        int w = host.contentW();
        fullscreen = Config.fullscreen;
        isolation = Config.defaultIsolation;

        memField = field(x + 40, rowY(0), 46, String.valueOf(Config.memMb), 5);
        threadField = field(x + 170, rowY(0), 30, String.valueOf(Config.downloadThreads), 2);

        // 即时落盘：以前只有点「保存」才写 Config，用户改完直接切页 → init() 又从 Config 读回旧值，
        // 看起来就是「改了内存，切一下回来又变 4096」。全屏/版本隔离按钮已经这么修过了，输入框一并改。
        bindInt(memField, () -> Config.memMb, v -> Config.memMb = v, 256, 65536);
        bindInt(threadField, () -> Config.downloadThreads, v -> Config.downloadThreads = v, 1, 64);

        jvmField = field(x + 58, rowY(1), w - 58, Config.jvmArgs, 512);
        gameField = field(x + 58, rowY(2), w - 58, Config.gameArgs, 512);
        bindText(jvmField, () -> Config.jvmArgs, v -> Config.jvmArgs = v);
        bindText(gameField, () -> Config.gameArgs, v -> Config.gameArgs = v);

        javaFieldPlaceholder(x, w);

        add(Btn.builder(Txt.of(fullscreen ? "全屏 ✓" : "窗口"), b -> {
            fullscreen = !fullscreen;
            Btn.label(b, fullscreen ? "全屏 ✓" : "窗口");
            // 立即落盘：不然切页/重建后 init() 会从 Config 把勾选状态读回去（等于白勾）
            Config.fullscreen = fullscreen;
            Config.save();
        }).dimensions(x + 40, rowY(4), 62, 18).build());

        wField = field(x + 122, rowY(4), 40, String.valueOf(Config.winWidth), 4);
        hField = field(x + 186, rowY(4), 40, String.valueOf(Config.winHeight), 4);
        bindInt(wField, () -> Config.winWidth, v -> Config.winWidth = v, 320, 7680);
        bindInt(hField, () -> Config.winHeight, v -> Config.winHeight = v, 240, 4320);

        add(Btn.builder(Txt.of("保存"), b -> save())
                .dimensions(x, rowY(5), 56, 18).build());

        add(Btn.builder(Txt.of(isolation ? "版本隔离 ✓" : "版本隔离"), b -> {
            isolation = !isolation;
            Btn.label(b, isolation ? "版本隔离 ✓" : "版本隔离");
            // 立即落盘（同上）：以前只改页内变量，切个页就“勾不见了”
            Config.defaultIsolation = isolation;
            Config.save();
            status = isolation ? "版本隔离已开（新装版本默认隔离）" : "版本隔离已关";
        }).dimensions(x + 60, rowY(5), 86, 18).build());

        add(Btn.builder(Txt.of(sourceLabel()), b -> {
            view = View.SOURCES;
            status = "";
            host.rebuild();
        }).dimensions(x + 150, rowY(5), 66, 18).build());

        add(Btn.builder(Txt.of("恢复默认"), b -> {
            Config.memMb = 4096;
            Config.jvmArgs = "";
            Config.gameArgs = "";
            Config.winWidth = 854;
            Config.winHeight = 480;
            Config.fullscreen = false;
            Config.downloadThreads = 16;
            Config.javaPath = "";
            Config.defaultIsolation = false;
            Config.save();
            status = "已恢复默认值";
            host.rebuild();
        }).dimensions(x + 219, rowY(5), 66, 18).build());

        // ---- 游戏目录
        // 右边两个按钮占 [w-130, w]：输入框宽必须留出这 130px（以前 w-128 直接盖住「检测」）
        dirField = field(x + 40, rowY(6), w - 174, UiState.dir().toString(), 260);
        add(Btn.builder(Txt.of("设为目录"), b -> setDir())
                .dimensions(x + w - 84, rowY(6), 84, 18).build());
        add(Btn.builder(Txt.of("检测"), b -> {
            if (Dirs.known().isEmpty()) {
                status = "没扫到常见目录";
                return;
            }
            dirField.setText(Dirs.known().get(0).toString());
            status = "填入了最近扫到的目录";
        }).dimensions(x + w - 130, rowY(6), 42, 18).build());
    }

    /** Java 这一行改成「只看不改」：真正的列表在「Java」页。 */
    private void javaFieldPlaceholder(int x, int w) {
        add(Btn.builder(Txt.of("选择…"), b -> host.switchTo("Java"))
                .dimensions(x + w - 44, rowY(3), 44, 18).build());
    }

    private void setDir() {
        String raw = dirField.getText().trim();
        if (raw.isEmpty()) {
            status = "目录不能为空";
            return;
        }
        try {
            java.nio.file.Path p = java.nio.file.Paths.get(raw).toAbsolutePath().normalize();
            java.nio.file.Files.createDirectories(p);
            UiState.switchDir(p);
            Config.lastDir = p.toString();
            Config.save();
            dirField.setText(p.toString());
            status = "已切换到 " + shorten(p.toString(), 30);
            host.rebuild();
        } catch (Exception ex) {
            status = "路径不对: " + ex.getMessage();
        }
    }

    private TextFieldWidget field(int x, int y, int w, String value, int max) {
        TextFieldWidget f = Field.of(this.tr(), x, y, w, 18, "");
        f.setMaxLength(max);
        f.setText(value == null ? "" : value);
        add(f);
        return f;
    }

    /**
     * 数字输入框即时写回 Config（并落盘）。
     * 打字中途的中间值（比如想打 8192，先敲成 "8"）不写，避免把 256/65536 这类磗值当用户意图。
     */
    private void bindInt(TextFieldWidget f, java.util.function.IntSupplier get,
                         java.util.function.IntConsumer set, int min, int max) {
        dev.mcver.ui.Field.changed(f, s -> {
            int v;
            try {
                v = Integer.parseInt(s == null ? "" : s.trim());
            } catch (NumberFormatException e) {
                return;
            }
            if (v < min || v > max || v == get.getAsInt()) return;
            set.accept(v);
            Config.save();
        });
    }

    /** 文本输入框即时写回 Config（并落盘），值没变就不写盘。 */
    private void bindText(TextFieldWidget f, java.util.function.Supplier<String> get,
                          java.util.function.Consumer<String> set) {
        dev.mcver.ui.Field.changed(f, s -> {
            String v = s == null ? "" : s.trim();
            if (v.equals(get.get())) return;
            set.accept(v);
            Config.save();
        });
    }

    private static String sourceLabel() {
        Sources.Src s = Sources.active();
        return s.official() ? "下载源: 官方" : "下载源: " + s.name();
    }

    private static String envSrc(String k) {
        String v = System.getenv(k);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    private void save() {
        Config.memMb = Config.clamp(parse(memField, Config.memMb), 256, 65536);
        Config.downloadThreads = Config.clamp(parse(threadField, Config.downloadThreads), 1, 64);
        Config.jvmArgs = jvmField.getText().trim();
        Config.gameArgs = gameField.getText().trim();
        Config.winWidth = Config.clamp(parse(wField, Config.winWidth), 320, 7680);
        Config.winHeight = Config.clamp(parse(hField, Config.winHeight), 240, 4320);
        Config.fullscreen = fullscreen;
        Config.defaultIsolation = isolation;
        Config.save();
        memField.setText(String.valueOf(Config.memMb));
        threadField.setText(String.valueOf(Config.downloadThreads));
        wField.setText(String.valueOf(Config.winWidth));
        hField.setText(String.valueOf(Config.winHeight));
        status = "已保存";
    }

    private static int parse(TextFieldWidget f, int def) {
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (Exception e) {
            return def;
        }
    }

    // ================================================================ 下载源视图

    private void initSources() {
        sources = new ArrayList<>(Sources.all());
        McVerMod.LOGGER.info("mcver: 下载源 {} 当前={}", sources.stream().map(Sources.Src::label).collect(java.util.stream.Collectors.toList()),
                Sources.active().label());
        int x = host.contentX();
        int w = host.contentW();

        add(Btn.builder(Txt.of("◀ 返回"), b -> {
            view = View.MAIN;
            status = "";
            host.rebuild();
        }).dimensions(x, host.contentY() + 1, 46, 16).build());

        add(Btn.builder(Txt.of("＋ BMCLAPI"), b -> {
            Sources.add(Sources.BMCLAPI.name(), Sources.BMCLAPI.root());
            // 加完直接启用，省一步
            Sources.setActive(Sources.BMCLAPI.name());
            status = "已添加并启用 BMCLAPI（国内镜像）";
            host.rebuild();
        }).dimensions(x + w - 84, host.contentY() + 1, 84, 16).build());

        int vis = srcRows();
        if (srcScroll > Math.max(0, sources.size() - vis)) srcScroll = Math.max(0, sources.size() - vis);
        Sources.Src active = Sources.active();
        int ry = srcTop();
        int to = Math.min(sources.size(), srcScroll + vis);
        for (int i = srcScroll; i < to; i++) {
            Sources.Src s = sources.get(i);
            boolean on = s.name().equalsIgnoreCase(active.name());
            boolean builtin = s.official();
            int rowW = builtin ? w : w - 16;
            add(Btn.builder(Txt.of(trim((on ? "▶ " : "   ") + s.label(), rowW - 6)), b -> {
                Sources.setActive(s.name());
                status = "已切到 " + s.name();
                host.rebuild();
            }).dimensions(x, ry, rowW, 16).build());
            if (!builtin) {
                add(Btn.builder(Txt.of("✕"), b -> {
                    Sources.remove(s.name());
                    if (Sources.active().official()) Sources.setActive("");
                    status = "已删除 " + s.name();
                    host.rebuild();
                }).dimensions(x + w - 14, ry, 14, 16).build());
            }
            ry += 18;
        }

        int y = host.contentBottom() - 36;
        srcNameField = Field.of(this.tr(), x, y, 76, 16, "名字");
        srcNameField.setMaxLength(24);
        srcNameField.setText("");
        add(srcNameField);

        srcUrlField = Field.of(this.tr(), x + 80, y, w - 80 - 48, 16, "镜像根地址");
        srcUrlField.setMaxLength(200);
        srcUrlField.setText("");
        add(srcUrlField);

        add(Btn.builder(Txt.of("添加"), b -> addSource())
                .dimensions(x + w - 44, y, 44, 16).build());

        // 自检钩子：MCVER_SRC_ADD="名字|根地址" → 自动走一遍添加
        String hook = envSrc("MCVER_SRC_ADD");
        if (hook != null && !srcHookTried) {
            srcHookTried = true;
            String[] parts = hook.split("\\|", 2);
            if (parts.length == 2) {
                srcNameField.setText(parts[0]);
                srcUrlField.setText(parts[1]);
                addSource();
            }
        }
    }

    private void addSource() {
        String name = srcNameField == null ? "" : srcNameField.getText().trim();
        String url = srcUrlField == null ? "" : srcUrlField.getText().trim();
        if (url.isEmpty()) {
            status = "根地址不能为空，例: https://bmclapi2.bangbang93.com";
            host.rebuild();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        if (name.isEmpty()) {
            name = url.replaceFirst("^https?://", "").split("/")[0];
        }
        Sources.Src probe = new Sources.Src(name, url);
        status = "正在测试 " + name + " …";
        host.rebuild();
        final String fn = name, fu = url;
        Thread t = new Thread(() -> {
            String msg;
            try {
                long ms = Sources.probe(probe);
                Sources.add(fn, fu);
                Sources.setActive(fn);
                msg = "已添加并启用 " + fn + "（" + ms + " ms）";
            } catch (Exception e) {
                msg = "测试失败: " + e.getMessage();
            }
            final String m = msg;
            dev.mcver.core.Ui.exec(MinecraftClient.getInstance(), () -> {
                status = m;
                host.rebuild();
            });
        }, "mcver-source-probe");
        t.setDaemon(true);
        t.start();
    }

    private void renderSources(Ctx ctx) {
        int x = host.contentX();
        int w = host.contentW();
        text(ctx, "下载源", x + 50, host.contentY() + 5, MainScreen.C_TEXT);
        // 状态行原来的 y 是 srcBottom()+6 = contentBottom()-36，正好压在下面两个输入框上。
        // 输入框占 contentBottom()-36 .. -20，所以状态行往下放到 -16。
        if (!status.isEmpty()) {
            text(ctx, trim(status, w), x, host.contentBottom() - 16, MainScreen.C_WARN);
        }
    }

    // ================================================================ render

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {
        if (view == View.SOURCES) { renderSources(ctx); return; }
        int x = host.contentX();
        int w = host.contentW();

        text(ctx, "内存", x, rowY(0) + 5, MainScreen.C_DIM);
        text(ctx, "MB", x + 90, rowY(0) + 5, MainScreen.C_FAINT);
        text(ctx, "下载线程", x + 120, rowY(0) + 5, MainScreen.C_DIM);

        text(ctx, "JVM 参数", x, rowY(1) + 5, MainScreen.C_DIM);
        text(ctx, "游戏参数", x, rowY(2) + 5, MainScreen.C_DIM);
        text(ctx, "Java", x, rowY(3) + 5, MainScreen.C_DIM);
        text(ctx, trim(javaSummary(), w - 106), x + 58, rowY(3) + 5, MainScreen.C_FAINT);
        text(ctx, "窗口", x, rowY(4) + 5, MainScreen.C_DIM);
        text(ctx, "宽", x + 106, rowY(4) + 5, MainScreen.C_FAINT);
        text(ctx, "高", x + 170, rowY(4) + 5, MainScreen.C_FAINT);
        text(ctx, "目录", x, rowY(6) + 5, MainScreen.C_DIM);

        if (!status.isEmpty()) {
            rightAligned(ctx, trim(status, w / 2), x + w, rowY(5) + 5, MainScreen.C_OK);
        }
    }

    private static String javaSummary() {
        String raw = Config.javaPath;
        if (raw == null || raw.trim().isEmpty()) return "自动（按版本要求挑）";
        java.nio.file.Path p = dev.mcver.core.JavaFinder.resolveExe(raw);
        if (p == null) return "指定的路径无效，已按自动处理";
        return "Java " + dev.mcver.core.JavaFinder.majorOfExe(p) + "  " + p;
    }

    private static String shorten(String s, int max) {
        return s == null || s.length() <= max ? String.valueOf(s) : "…" + s.substring(s.length() - max + 1);
    }
}
