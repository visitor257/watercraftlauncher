package dev.mcver.ui;

import dev.mcver.core.Config;
import dev.mcver.core.Dirs;
import dev.mcver.core.Jobs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 游戏内的启动器主界面：一块 400×232 的原版风格面板，左边导航，右边是页。
 * 文字一律不画阴影（浅灰底上深字加影子会糊成重影）。
 */
public class MainScreen extends ChromeScreen {

    public static final int PANEL_W = 400;
    public static final int PANEL_H = 232;
    public static final int HEADER_H = 30;
    public static final int PAD = 8;
    public static final int NAV_W = 62;
    public static final int NAV_ITEM = 18;

    public static final int C_TEXT = 0xFF404040;
    public static final int C_DIM = 0xFF6E6E6E;
    public static final int C_FAINT = 0xFF8A8A8A;
    public static final int C_ERR = 0xFFA00000;
    public static final int C_OK = 0xFF1F6B1F;
    public static final int C_WARN = 0xFF8A5A00;

    private final List<Page> pages = new ArrayList<>();
    private int active;

    public MainScreen(Screen parent) {
        super(Txt.of("Water Craft Launcher"));
        this.parentScreen = parent;
        Config.load();
        restoreDir();
        pages.add(new LaunchPage(this));
        pages.add(new DownloadPage(this));
        pages.add(new AccountsPage(this));
        pages.add(new VersionsPage(this));
        pages.add(new JavaPage(this));
        pages.add(new SettingsPage(this));
        String want = env("MCVER_TAB");
        if (want != null) {
            for (int i = 0; i < pages.size(); i++) {
                if (pages.get(i).nav().equals(want)) { active = i; break; }
            }
        }
    }

    /** 上次用的目录还在，就接着用它，否则用跑着的这个实例的目录。 */
    private static void restoreDir() {
        String last = Config.lastDir;
        if (last != null && !last.trim().isEmpty()) {
            try {
                Path p = Paths.get(last).toAbsolutePath().normalize();
                if (Files.isDirectory(p)) {
                    UiState.setDir(p);
                    // 登记进 dirs：不然用户一点「添加」，这个目录就从列表里消失了
                    Dirs.add(p);
                    return;
                }
            } catch (Exception ignored) { }
        }
        UiState.setDir(MinecraftClient.getInstance().runDirectory.toPath());
        Dirs.touchHistory(UiState.dir());
    }

    private static String env(String k) {
        String v = System.getenv(k);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    public int activeIndex() { return active; }

    // ================================================================ 几何

    public int pw() { return Math.min(Math.min(PANEL_W, this.width - 6), this.width * 88 / 100); }
    public int ph() { return Math.min(Math.min(PANEL_H, this.height - 6), this.height * 92 / 100); }
    public int px() { return (this.width - pw()) / 2; }
    public int py() { return (this.height - ph()) / 2; }
    public int left() { return px() + PAD; }
    public int right() { return px() + pw() - PAD; }

    public int contentX() { return px() + PAD + NAV_W + 6; }
    public int contentW() { return right() - contentX(); }
    public int contentY() { return py() + HEADER_H; }
    public int contentBottom() { return py() + ph() - PAD; }

    // ================================================================ init

    @Override
    public void init() {
        // 右上角关闭：只是把界面收起来，后台安装任务不受影响
        addWidget(Btn.builder(Txt.of("✕"), b -> this.closeSelf())
                .dimensions(px() + pw() - 16, py() + 4, 13, 13).build());

        int y = contentY() + 2;
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            String label = (idx == active ? "▶" : "  ") + pages.get(i).nav();
            addWidget(Btn.builder(Txt.of(label), b -> switchTo(idx))
                    .dimensions(px() + PAD, y, NAV_W, NAV_ITEM - 3).build());
            y += NAV_ITEM;
        }
        pages.get(active).init();
        debugDump();
    }

    public void switchTo(int i) {
        if (i < 0 || i >= pages.size() || i == active) return;
        pages.get(active).onLeave();
        active = i;
        rebuild();
    }

    /** 按导航名切页（“启动” / “下载” …）。 */
    public void switchTo(String nav) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).nav().equals(nav)) { switchTo(i); return; }
        }
    }

    public void rebuild() {
        this.clearWidgets();
        this.init();
    }

    /** 给 Page 用的公开入口（基类的 addWidget 是 protected）。 */
    public void add(Object w) {
        addWidget(w);
    }

    // ================================================================ 事件

    @Override
    public void tick() {
        super.tick();
        tickCount++;
        pages.get(active).tick();
        // 自检：MCVER_TAB_CYCLE=1 时每 2 秒切一页，把每页都跑一遍
        if (System.getenv("MCVER_TAB_CYCLE") != null && tickCount % 40 == 0) {
            switchTo((active + 1) % pages.size());
        }
    }

    private int tickCount;
    private boolean chromeLogged;
    private boolean contentLogged;

    /** 滚轮转给当前页（ChromeScreen 屏蔽了 1.20.1 / 1.21 的签名差异）。 */
    @Override
    protected boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical) {
        return pages.get(active).mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** ESC 关界面。签名差异在 ChromeScreen 的 keyPressedDefault 里处理掉了。 */
    @Override
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.shouldCloseOnEsc()) { // ESC
            this.closeSelf();
            return true;
        }
        return super.keyPressedDefault(keyCode, scanCode, modifiers);
    }

    // ================================================================ 渲染

    /** 面板底 + 标题栏 + 导航分隔线（ChromeScreen 负责在正确的时机调它）。 */
    @Override
    protected void drawChrome(Ctx ctx) {
        if (!chromeLogged) {
            chromeLogged = true;
            dev.mcver.McVerMod.LOGGER.info("mcver: screen=MAIN chrome 已绘制");
        }
        drawPanel(ctx);
        drawHeader(ctx);
        drawNavDivider(ctx);
    }

    @Override
    protected void renderContent(Ctx ctx, int mouseX, int mouseY, float delta) {
        if (!contentLogged) {
            contentLogged = true;
            dev.mcver.McVerMod.LOGGER.info("mcver: screen=MAIN 首帧渲染完成");
        }
        pages.get(active).render(ctx, mouseX, mouseY, delta);
        // 自检：MCVER_ICON=1 → 右下角画一下物品图标，用来确认贴图不是紫黑错误块
        if (System.getenv("MCVER_ICON") != null) {
            ItemStack st = new ItemStack(dev.mcver.McVerMod.DOWNLOAD_TOOL);
            int ix = px() + pw() - 24, iy = py() + ph() - 24;
            ctx.fill(ix - 22, iy - 2, ix - 2, iy + 18, 0xFF00FF00); // 定标：绿块在图标左边，不遮挡
            ctx.drawItem(st, ix - 80, iy);
            ctx.drawItem(new ItemStack(net.minecraft.item.Items.PAPER), ix - 40, iy);
            Object sid = dev.mcver.core.Compat.itemId(st.getItem());
            dev.mcver.McVerMod.LOGGER.info("mcver: 图标自检 item={} stack={} paper=", sid, st);
        }
    }

    private void drawPanel(Ctx ctx) {
        int x = px(), y = py(), w = pw(), h = ph();
        ctx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x50000000);
        ctx.fill(x, y, x + w, y + h, 0xFF000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFFFFFFF);
        ctx.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFFFFFFF);
        ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        ctx.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, 0xFF555555);
    }

    private void drawHeader(Ctx ctx) {
        int x = px(), y = py(), w = pw();
        String title = "Water Craft Launcher";

        // 左上角放物品图标（流动的水），标题往右让出位置
        ctx.drawItem(new ItemStack(dev.mcver.McVerMod.DOWNLOAD_TOOL), x + PAD, y + 5);
        int tx = x + PAD + 20;

        drawText(ctx, title, tx, y + 6, C_TEXT);

        dev.mcver.core.Account acc = Config.account();
        String who = acc != null ? acc.name + (acc.isMsa() ? " (正版)" : " (离线)")
                : MinecraftClient.getInstance().getSession().getUsername();
        who = trim(who, 96);
        rightAlignedText(ctx, who, x + w - PAD - 16, y + 6, C_DIM);


        String dir = shorten(UiState.dir().toString(), 40);
        drawText(ctx, trim(dir, 170), tx, y + 17, C_FAINT);

        String bg = Jobs.brief();
        if (!bg.trim().isEmpty()) {
            int color = Jobs.running() ? C_WARN : (bg.startsWith("失败") ? C_ERR : C_OK);
            rightAlignedText(ctx, trim(bg, 130), x + w - PAD - 16, y + 17, color);
        }

        ctx.fill(x + PAD, y + HEADER_H - 5, x + w - PAD, y + HEADER_H - 4, 0xFF8B8B8B);
        ctx.fill(x + PAD, y + HEADER_H - 4, x + w - PAD, y + HEADER_H - 3, 0xFFFFFFFF);
    }

    private void drawNavDivider(Ctx ctx) {
        int x = px() + PAD + NAV_W + 2;
        int y0 = contentY() + 2, y1 = contentBottom();
        ctx.fill(x, y0, x + 1, y1, 0xFF8B8B8B);
        ctx.fill(x + 1, y0, x + 2, y1, 0xFFFFFFFF);
    }

    // ---- 文字（无阴影）

    private void drawText(Ctx ctx, String s, int x, int y, int color) {
        ctx.drawText(this.textRenderer, Txt.of(s), x, y, color, false);
    }

    private void rightAlignedText(Ctx ctx, String s, int rx, int y, int color) {
        ctx.drawText(this.textRenderer, Txt.of(s), rx - Measure.width(this.textRenderer, s), y, color, false);
    }

    private String trim(String s, int w) {
        return this.textRenderer.trimToWidth(s, w);
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    // ================================================================ 自动化钩子

    public void debugDump() {
        try {
            String line = String.format("%s screen=MAIN tab=%s gui=%dx%d panel=%dx%d@%d,%d children=%d%n",
                    java.time.LocalTime.now().withNano(0), pages.get(active).nav(),
                    this.width, this.height, pw(), ph(), px(), py(), this.children().size());
            dev.mcver.core.Jdk.writeAppend(Paths.get("mcver-debug.txt").toAbsolutePath(), line);
            // 同时进游戏日志：无头验证（ProbeLaunch 的 waitFor=screen=MAIN）只看得到日志
            dev.mcver.McVerMod.LOGGER.info("mcver: {}", line.trim());
        } catch (Exception ignored) { }
    }
}
