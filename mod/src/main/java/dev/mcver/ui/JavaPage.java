package dev.mcver.ui;

import dev.mcver.core.Config;
import dev.mcver.core.JavaFinder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 「Java」页：把机器上能找到的 Java 全列出来，点一下就指定用它；
 * 也可以从别处粘 java.exe / javaw.exe 的路径（Linux 上填 .../bin/java）。
 *
 * 默认是「自动」：按版本 json 里的 javaVersion 挑。
 */
public class JavaPage extends Page {

    private List<JavaFinder.Found> list = List.of();
    private int page;
    private TextFieldWidget addField;
    private String status = "";
    private int statusColor = MainScreen.C_DIM;

    public JavaPage(MainScreen host) {
        super(host);
    }

    @Override
    public String nav() { return "Java"; }

    // ================================================================ 几何

    private int listTop() { return host.contentY() + 22; }
    private int footY() { return host.contentBottom() - 18; }
    private int statusY() { return footY() - 12; }
    private int rowsVisible() {
        return Math.max(1, (statusY() - 2 - listTop()) / 18);
    }
    private int pages() { return Math.max(1, (list.size() + rowsVisible() - 1) / rowsVisible()); }

    // ================================================================ init

    @Override
    public void init() {
        list = JavaFinder.all();

        int x = host.contentX();
        int w = host.contentW();

        boolean auto = selected() == null;
        add(ButtonWidget.builder(Text.literal(auto ? "▶ 自动（按版本挑）" : "  自动（按版本挑）"), b -> {
            Config.javaPath = "";
            Config.save();
            status = "已改回自动：按版本要求挑 Java";
            statusColor = MainScreen.C_DIM;
            host.rebuild();
        }).dimensions(x, host.contentY() + 1, 118, 16).build());

        add(ButtonWidget.builder(Text.literal("重新扫描"), b -> {
            JavaFinder.invalidate();
            page = 0;
            status = "已重新扫描";
            statusColor = MainScreen.C_DIM;
            host.rebuild();
        }).dimensions(x + w - 60, host.contentY() + 1, 60, 16).build());

        // 翻页：和「下载」页一样 ◀ ▶ + 页码，而不是只能默默滚滚轮
        int pagesN = pages();
        if (page > pagesN - 1) page = pagesN - 1;
        if (page < 0) page = 0;
        add(ButtonWidget.builder(Text.literal("◀"), b -> {
            if (page > 0) { page--; host.rebuild(); }
        }).dimensions(x + 122, host.contentY() + 1, 16, 16).build());
        add(ButtonWidget.builder(Text.literal("▶"), b -> {
            if (page < pagesN - 1) { page++; host.rebuild(); }
        }).dimensions(x + 140, host.contentY() + 1, 16, 16).build());

        int vis = rowsVisible();
        int from = page * vis;
        int to = Math.min(list.size(), from + vis);

        Path sel = selected();
        int ry = listTop();
        for (int i = from; i < to; i++) {
            JavaFinder.Found f = list.get(i);
            boolean on = sel != null && f.exe().toAbsolutePath().normalize().equals(sel);
            boolean custom = isCustom(f.exe());
            int rowW = custom ? w - 16 : w;
            String label = (on ? "▶ " : "   ") + "Java " + f.major()
                    + (f.arch() == null || f.arch().isBlank() ? "" : " " + f.arch())
                    + "   " + javaHomeName(f.exe()) + "   " + f.exe();
            add(ButtonWidget.builder(Text.literal(trim(label, rowW - 8)), b -> {
                Config.javaPath = f.exe().toString();
                Config.save();
                status = "已指定 Java " + f.major();
                statusColor = MainScreen.C_OK;
                host.rebuild();
            }).dimensions(x, ry, rowW, 16).build());

            if (custom) {
                add(ButtonWidget.builder(Text.literal("✕"), b -> {
                    Config.javaExtra.removeIf(s -> {
                        Path p = JavaFinder.resolveExe(s);
                        return p != null && p.toAbsolutePath().normalize().equals(f.exe().toAbsolutePath().normalize());
                    });
                    Config.save();
                    JavaFinder.invalidate();
                    status = "已移出列表";
                    statusColor = MainScreen.C_DIM;
                    host.rebuild();
                }).dimensions(x + w - 14, ry, 14, 16).build());
            }
            ry += 18;
        }

        addField = new TextFieldWidget(this.tr(), x, footY(), w - 66, 18, Text.literal("java 路径"));
        addField.setMaxLength(260);
        addField.setText("");
        add(addField);

        add(ButtonWidget.builder(Text.literal("添加"), b -> addFromField())
                .dimensions(x + w - 62, footY(), 62, 18).build());
    }

    private static String javaHomeName(Path exe) {
        Path bin = exe.getParent();
        Path home = bin == null ? null : bin.getParent();
        return home == null || home.getFileName() == null ? "" : home.getFileName().toString();
    }

    private Path selected() {
        if (Config.javaPath == null || Config.javaPath.isBlank()) return null;
        Path p = JavaFinder.resolveExe(Config.javaPath);
        return p == null ? null : p.toAbsolutePath().normalize();
    }

    private boolean isCustom(Path exe) {
        for (String s : Config.javaExtra) {
            Path p = JavaFinder.resolveExe(s);
            if (p != null && p.toAbsolutePath().normalize().equals(exe.toAbsolutePath().normalize())) return true;
        }
        return false;
    }

    private void addFromField() {
        String raw = addField == null ? "" : addField.getText().trim();
        if (raw.isEmpty()) {
            status = "先粘一个 java.exe / javaw.exe 路径（也可以填 JDK 目录）";
            statusColor = MainScreen.C_ERR;
            host.rebuild();
            return;
        }
        Path exe = JavaFinder.resolveExe(raw);
        if (exe == null) {
            status = "这个路径下没找到 java / java.exe / javaw.exe";
            statusColor = MainScreen.C_ERR;
            host.rebuild();
            return;
        }
        int major = JavaFinder.majorOfExe(exe);
        if (major <= 0) {
            status = "找到了文件，但读不出版本号（不像是 Java）";
            statusColor = MainScreen.C_ERR;
            host.rebuild();
            return;
        }
        String norm = exe.toAbsolutePath().normalize().toString();
        Config.javaExtra.removeIf(s -> norm.equalsIgnoreCase(s));
        Config.javaExtra.add(0, norm);
        Config.javaPath = norm;
        Config.save();
        JavaFinder.invalidate();
        status = "已添加并用 Java " + major;
        statusColor = MainScreen.C_OK;
        host.rebuild();
    }

    // ================================================================ render

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {
        int x = host.contentX();
        int w = host.contentW();

        Path sel = selected();
        if (list.isEmpty()) {
            text(ctx, "一个 Java 都没找到", x, listTop() + 4, MainScreen.C_DIM);
            text(ctx, "在下面粘一个路径，或者点右上角重新扫描", x, listTop() + 16, MainScreen.C_FAINT);
        }

        rightAligned(ctx, (page + 1) + "/" + pages(), x + w - 64, host.contentY() + 5, MainScreen.C_FAINT);

        String cur = sel == null
                ? "自动"
                : "Java " + JavaFinder.majorOfExe(sel);
        rightAligned(ctx, "当前：" + cur, x + w, statusY(), MainScreen.C_FAINT);

        if (!status.isEmpty()) {
            text(ctx, trim(status, w - 74), x, statusY(), statusColor);
        } else if (!list.isEmpty()) {
            text(ctx, "第 " + (page + 1) + "/" + pages() + " 页 · 共 " + list.size() + " 个",
                    x, statusY(), MainScreen.C_FAINT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // 滚轮也顺手翻页，但列表本身走 ◀ ▶
        int d = (int) Math.signum(vertical);
        if (d == 0) return false;
        int npage = Math.max(0, Math.min(pages() - 1, page - d));
        if (npage == page) return false;
        page = npage;
        host.rebuild();
        return true;
    }
}
