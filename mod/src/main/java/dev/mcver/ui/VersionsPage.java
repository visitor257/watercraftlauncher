package dev.mcver.ui;

import dev.mcver.McVerMod;
import dev.mcver.core.Config;
import dev.mcver.core.Installer;
import dev.mcver.core.Launcher;
import dev.mcver.core.Sources;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 「版本」页：重命名 / 删除 / 补全文件 / 打开目录 / 版本隔离。 */
public class VersionsPage extends Page {

    private final List<String> versions = new ArrayList<>();
    private String selected;
    private int scroll;

    private boolean renaming;
    private TextFieldWidget renameField;
    private boolean confirmingDelete;

    private String status = "";
    private int statusColor = MainScreen.C_DIM;
    /** 被隐藏的版本数（加载器版本引用的原版），只在底部提一句。 */
    private int hiddenCount;
    private volatile boolean busy;
    private boolean hookDelTried;
    private boolean hookIsoTried;

    public VersionsPage(MainScreen host) {
        super(host);
    }

    @Override
    public String nav() { return "版本"; }

    private int listTop() { return host.contentY() + 2; }
    private int bottomY() { return host.contentBottom() - 18; }
    private int statusY() { return bottomY() - 12; }
    private int rowsVisible() { return Math.max(1, (statusY() - 2 - listTop()) / 18); }

    private Path versionsDir() { return UiState.dir().resolve("versions"); }

    // ================================================================ init

    @Override
    public void init() {
        refresh();
        // 自检钩子：选版本 / 删版本 / 切隔离
        String vsel = env("MCVER_VSEL");
        if (vsel != null && versions.contains(vsel)) selected = vsel;
        if (env("MCVER_VDEL_OK") != null && !hookDelTried) {
            hookDelTried = true;
            deleteSelected();
            return;
        }
        if (env("MCVER_VDEL") != null && !hookDelTried) {
            hookDelTried = true;
            confirmingDelete = true;
        }
        if (env("MCVER_VISO") != null && !hookIsoTried) {
            hookIsoTried = true;
            toggleIsolation();
            return;
        }
        int x = host.contentX();
        int w = host.contentW();

        int vis = rowsVisible();
        int maxScroll = Math.max(0, versions.size() - vis);
        if (scroll > maxScroll) scroll = maxScroll;
        if (scroll < 0) scroll = 0;

        int ry = listTop();
        int to = Math.min(versions.size(), scroll + vis);
        for (int i = scroll; i < to; i++) {
            String id = versions.get(i);
            boolean sel = id.equals(selected);
            boolean done = Launcher.isComplete(UiState.dir(), id);
            String label = (sel ? "▶ " : "   ") + id + (done ? "" : "   · 未完成");
            add(Btn.builder(Txt.of(trim(label, w - 8)), b -> {
                selected = id;
                renaming = false;
                confirmingDelete = false;
                status = "";
                host.rebuild();
            }).dimensions(x, ry, w, 16).build());
            ry += 18;
        }

        if (renaming) {
            renameField = new TextFieldWidget(this.tr(), x, bottomY(), w - 96, 18, Txt.of("新版本名"));
            renameField.setMaxLength(64);
            renameField.setText(selected == null ? "" : selected);
            add(renameField);

            add(Btn.builder(Txt.of("确定"), b -> applyRename())
                    .dimensions(x + w - 92, bottomY(), 44, 18).build());
            add(Btn.builder(Txt.of("取消"), b -> {
                renaming = false;
                status = "";
                host.rebuild();
            }).dimensions(x + w - 44, bottomY(), 44, 18).build());
            return;
        }

        if (confirmingDelete) {
            int y = bottomY();
            String who = selected == null ? "" : selected;
            add(Btn.builder(Txt.of("确定永久删除"), b -> deleteSelected())
                    .dimensions(x, y, 100, 18).build());
            add(Btn.builder(Txt.of("取消"), b -> {
                confirmingDelete = false;
                status = "";
                host.rebuild();
            }).dimensions(x + 104, y, 50, 18).build());
            return;
        }

        // 底部七个按钮平分宽度（屏小/面板窄时也不会溢出）
        boolean selHidden = selected != null && Launcher.isHidden(UiState.dir(), selected);
        // 选中的是「被某个加载器版本继承的原版」时，按钮变成「合并」：
        // 把它并进加载器版本，然后把自身目录删掉（磁盘上就只剩一个版本）
        String child = selected == null ? null : Launcher.parentOf(UiState.dir(), selected);
        String lastLabel = child != null ? "合并" : (selHidden ? "显示" : "隐藏");
        String[] labels = {"重命名", "删除", "补全", "文件夹", "日志",
                isIsolated() ? "隔离✓" : "隔离", lastLabel};
        int gap = 2;
        int bw = (w - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            int bx = x + i * (bw + gap);
            add(Btn.builder(Txt.of(labels[i]), b -> {
                switch (idx) {
                    case 0: {
                        if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; return; }
                        renaming = true;
                        status = "";
                        host.rebuild();
                        break;
                    }
                    case 1: {
                        if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; return; }
                        confirmingDelete = true;
                        status = "";
                        host.rebuild();
                        break;
                    }
                    case 2: completeSelected(); break;
                    case 3: openFolder(); break;
                    case 4: openLaunchLog(); break;
                    case 5: toggleIsolation(); break;
                    default: {
                        if (child != null) mergeInto(child); else toggleHidden();
                        break;
                    }
                }
            }).dimensions(bx, bottomY(), bw, 18).build());
        }
    }

    /** 把选中的原版并进继承它的加载器版本，然后删掉原版目录。 */
    private void mergeInto(String childId) {
        if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; return; }
        String vanilla = selected;
        try {
            if (Launcher.flatten(UiState.dir(), childId, vanilla)) {
                status = "已合并到 " + childId + "，versions/" + vanilla + " 已移除";
                statusColor = MainScreen.C_OK;
                selected = childId;
            } else {
                status = "合并失败：还有别的版本在引用 " + vanilla;
                statusColor = MainScreen.C_ERR;
            }
        } catch (Exception e) {
            status = "合并失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
        host.rebuild();
    }

    /** 把一个版本从列表里隐藏/显示（文件不动）。用于清掉加载器版本旁边多出来的那个原版。 */
    private void toggleHidden() {        if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; return; }
        boolean on = !Launcher.isHidden(UiState.dir(), selected);
        try {
            Launcher.setHidden(UiState.dir(), selected, on);
            status = on ? "已隐藏 " + selected + "（文件还在，只是不在列表里）" : "已显示 " + selected;
            statusColor = MainScreen.C_OK;
            if (on) selected = null;
        } catch (Exception e) {
            status = (on ? "隐藏" : "显示") + "失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
        host.rebuild();
    }

    private void toggleIsolation() {
        if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; return; }
        boolean on = !isIsolated();
        try {
            Launcher.setIsolated(UiState.dir(), selected, on);
            status = on
                    ? "已开启版本隔离：存档/Mod 用 versions/" + selected + "，assets 仍共用"
                    : "已关闭版本隔离：改用共用目录启动（versions/" + selected + " 里的文件没删）";
            statusColor = MainScreen.C_OK;
        } catch (Exception e) {
            status = (on ? "开启" : "关闭") + "隔离失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
        host.rebuild();
    }

    private void refresh() {
        // 「版本」页把没装完的也列出来（带 ⚠），这样才能删掉或补全；
        // 但被隐藏的原版（加载器装完后留下的）不列，只给一行提示。
        List<String> all = Launcher.installedAll(UiState.dir());
        versions.clear();
        int skipped = 0;
        for (String id : all) {
            // 被加载器版本继承的原版：即使打了隐藏标记也要列出来，
            // 不然用户没地方点「合并」把它并进加载器版本然后删掉
            if (Launcher.isHidden(UiState.dir(), id) && Launcher.parentOf(UiState.dir(), id) == null) {
                skipped++;
                continue;
            }
            versions.add(id);
        }
        hiddenCount = skipped;
        if (selected == null && !versions.isEmpty()) selected = versions.get(0);
        if (selected != null && !versions.contains(selected)) {
            selected = versions.isEmpty() ? null : versions.get(0);
        }
    }

    private boolean isIsolated() {
        return selected != null && Launcher.isIsolated(UiState.dir(), selected);
    }

    // ================================================================ 动作

    private void applyRename() {
        if (selected == null || renameField == null) return;
        String neu = renameField.getText().trim();
        if (neu.isEmpty() || neu.equals(selected)) {
            renaming = false;
            host.rebuild();
            return;
        }
        if (!neu.matches("[A-Za-z0-9._\\-+ ]{1,64}")) {
            status = "名字里不能有 \\ / : * ? \" < > |";
            statusColor = MainScreen.C_ERR;
            return;
        }
        Path from = versionsDir().resolve(selected);
        Path to = versionsDir().resolve(neu);
        if (Files.exists(to)) {
            status = "已经有一个叫 " + neu + " 的版本了";
            statusColor = MainScreen.C_ERR;
            return;
        }
        try {
            Files.move(from, to);
            Path oldJson = to.resolve(selected + ".json");
            Path newJson = to.resolve(neu + ".json");
            if (Files.isRegularFile(oldJson)) {
                String text = dev.mcver.core.Jdk.readString(oldJson, StandardCharsets.UTF_8);
                text = text.replaceFirst("(\"id\"\\s*:\\s*\")" + java.util.regex.Pattern.quote(selected) + "(\")",
                        "$1" + neu.replace("\\", "\\\\").replace("$", "\\$") + "$2");
                dev.mcver.core.Jdk.writeString(newJson, text, StandardCharsets.UTF_8);
                if (!oldJson.equals(newJson)) Files.deleteIfExists(oldJson);
            }
            status = "已重命名为 " + neu;
            statusColor = MainScreen.C_OK;
            selected = neu;
            renaming = false;
            host.rebuild();
        } catch (Exception e) {
            status = "重命名失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
    }

    private void deleteSelected() {
        if (selected == null) {
            status = "先选一个版本";
            statusColor = MainScreen.C_ERR;
            return;
        }
        final String id = selected;
        try {
            Path src = versionsDir().resolve(id);
            deleteTree(src);
            confirmingDelete = false;
            status = "已删除 " + id + "（不可恢复）";
            statusColor = MainScreen.C_OK;
            selected = null;
            host.rebuild();
        } catch (Exception e) {
            confirmingDelete = false;
            status = "删除失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
            host.rebuild();
        }
    }

    /** 真删（递归），不做回收站。 */
    private static void deleteTree(Path root) throws java.io.IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<java.nio.file.Path> s = Files.walk(root)) {
            for (Path p : s.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** 打开「启动」页最近一次生成的 mcver-launch-*.log（启动不了时看这个）。 */
    private void openLaunchLog() {
        Path logs = runDirOf(selected).resolve("logs");
        Path newest = null;
        try (java.util.stream.Stream<java.nio.file.Path> s = Files.list(logs)) {
            newest = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("mcver-launch-"))
                    .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        } catch (Exception ignored) { }
        if (newest == null) {
            Path latest = logs.resolve("latest.log");
            newest = Files.isRegularFile(latest) ? latest : null;
        }
        if (newest == null) {
            status = "还没有启动日志（先点一次「启动」）";
            statusColor = MainScreen.C_ERR;
            return;
        }
        try {
            if (!dev.mcver.core.Open.path(newest)) throw new java.io.IOException("系统命令启动失败");
            status = "已打开 " + newest.getFileName();
            statusColor = MainScreen.C_DIM;
        } catch (Exception e) {
            status = "打开失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
    }

    private Path runDirOf(String id) {
        if (id == null) return UiState.dir();
        // 和启动器保持一致：开了版本隔离就去 versions/<id> 找日志
        if (Launcher.isIsolated(UiState.dir(), id)) return versionsDir().resolve(id);
        return UiState.dir();
    }

    private void openFolder() {
        if (selected == null) {
            status = "先选一个版本";
            statusColor = MainScreen.C_ERR;
            return;
        }
        try {
            if (!dev.mcver.core.Open.path(versionsDir().resolve(selected))) throw new java.io.IOException("系统命令启动失败");
            status = "已打开版本目录";
            statusColor = MainScreen.C_DIM;
        } catch (Exception e) {
            status = "打开失败: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
        }
    }

    private void completeSelected() {
        if (busy || selected == null) {
            if (selected == null) { status = "先选一个版本"; statusColor = MainScreen.C_ERR; }
            return;
        }
        final String id = selected;
        Path json = versionsDir().resolve(id).resolve(id + ".json");
        try {
            if (!Files.isRegularFile(json)) {
                status = "这个版本没有 " + id + ".json，没法补全";
                statusColor = MainScreen.C_ERR;
                return;
            }
            String text = dev.mcver.core.Jdk.readString(json, StandardCharsets.UTF_8);
            if (text.contains("\"inheritsFrom\"")) {
                status = "加载器版本请用「下载版本」重装一遍";
                statusColor = MainScreen.C_ERR;
                return;
            }
        } catch (Exception e) {
            status = "读不到版本 json: " + e.getMessage();
            statusColor = MainScreen.C_ERR;
            return;
        }

        busy = true;
        status = "正在补全 " + id + " …";
        statusColor = MainScreen.C_WARN;
        final Path dir = UiState.dir();
        final int jobs = Config.downloadThreads;

        Thread t = new Thread(() -> {
            try {
                Installer.install(dir, id, Sources.active(), true, false, jobs,
                        (stage, done, total, detail) -> {
                            if (done % 50 == 0 || total < 20) {
                                MinecraftClient.getInstance().execute(() -> {
                                    status = stage + " " + done + "/" + total;
                                    statusColor = MainScreen.C_WARN;
                                });
                            }
                        });
                busy = false;
                MinecraftClient.getInstance().execute(() -> {
                    status = id + " 已补全";
                    statusColor = MainScreen.C_OK;
                });
            } catch (Exception e) {
                McVerMod.LOGGER.warn("mcver: 补全失败 {}", e.getMessage());
                busy = false;
                MinecraftClient.getInstance().execute(() -> {
                    status = "补全失败: " + e.getMessage();
                    statusColor = MainScreen.C_ERR;
                });
            }
        }, "mcver-complete");
        t.setDaemon(true);
        t.start();
    }

    // ================================================================ render

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {
        int x = host.contentX();
        int w = host.contentW();

        if (versions.isEmpty()) {
            text(ctx, "versions/ 里没有装好的版本", x, listTop() + 4, MainScreen.C_DIM);
            text(ctx, "去「启动」页点「下载版本」装一个", x, listTop() + 16, MainScreen.C_FAINT);
        }

        String hint = selected == null ? "" : (isIsolated() ? "版本隔离" : "共用主目录");
        if (selected != null && !Launcher.isComplete(UiState.dir(), selected)) {
            hint = "未装完 · 点补全";
        }
        if (!hint.isEmpty()) {
            rightAligned(ctx, hint, x + w, listTop(), MainScreen.C_FAINT);
        }

        if (confirmingDelete && selected != null) {
            text(ctx, "永久删除 " + selected + "？文件直接从磁盘删掉，不可恢复",
                    x, bottomY() - 12, MainScreen.C_ERR);
            return;
        }

        text(ctx, trim(status, w), x, statusY(),
                status.isEmpty() ? MainScreen.C_FAINT : statusColor);
        if (hiddenCount > 0) {
            rightAligned(ctx, "另有 " + hiddenCount + " 个原版被加载器版本引用（已隐藏）",
                    x + w, statusY(), MainScreen.C_FAINT);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int vis = rowsVisible();
        if (versions.size() <= vis) return false;
        scroll = Math.max(0, Math.min(versions.size() - vis, scroll - (int) Math.signum(vertical)));
        host.rebuild();
        return true;
    }
}
