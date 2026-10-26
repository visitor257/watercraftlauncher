package dev.mcver.ui;

import dev.mcver.McVerMod;
import dev.mcver.core.Account;
import dev.mcver.core.Auth;
import dev.mcver.core.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.List;

/** 「账号」页：离线账号 + 微软正版登录（设备码流程）。 */
public class AccountsPage extends Page {

    private TextFieldWidget nameField;
    private String selectedName;   // 用名字标记选中，避免重建后引用错位
    private int page;
    private String status = "";
    private int statusColor = MainScreen.C_DIM;

    // ---- 微软登录状态
    private Auth.Device device;
    private volatile boolean msWaiting;
    private volatile boolean msCancel;
    private String msStatus = "";
    private String msError = "";

    public AccountsPage(MainScreen host) {
        super(host);
    }

    @Override
    public String nav() { return "账号"; }

    // ================================================================ 几何

    private int listTop() { return host.contentY() + 2; }
    // 名字/按钮/状态全部到底部：列表占中间，账号多了就分页，不会把按钮顶出去
    private int nameRowY() { return host.contentBottom() - 72; }
    private int btnRowY() { return host.contentBottom() - 50; }
    private int statusY() { return host.contentBottom() - 22; }
    private int msTop() { return listTop(); }
    private int listRows() { return Math.max(1, (nameRowY() - 6 - listTop()) / 18); }
    private int listPages() { return Math.max(1, (Config.accounts.size() + listRows() - 1) / listRows()); }

    // ================================================================ init

    @Override
    public void init() {
        int x = host.contentX();
        int w = host.contentW();

        Account cur = Config.account();
        if (selectedName == null && cur != null) selectedName = cur.name;

        // ---- 账号列表（分页，占中间整块区域）
        List<Account> list = Config.accounts;
        int rows = listRows();
        if (page > listPages() - 1) page = listPages() - 1;
        if (page < 0) page = 0;
        int from = page * rows;
        int to = Math.min(list.size(), from + rows);
        int ry = listTop();
        for (int i = from; i < to; i++) {
            Account a = list.get(i);
            boolean sel = a.name.equals(selectedName);
            String label = (sel ? "▶ " : "   ") + a.label();
            add(Btn.builder(Txt.of(trim(label, w - 8)), b -> {
                selectedName = a.name;
                status = "";
                host.rebuild();
            }).dimensions(x, ry, w, 16).build());
            ry += 18;
        }
        if (list.isEmpty()) {
            // 只有提示文字，画在 render 里
        }

        if (msWaiting) {
            // ---- 微软登录进行中：只留「复制 / 打开 / 取消」
            add(Btn.builder(Txt.of("复制代码"), b -> {
                if (device != null) MinecraftClient.getInstance().keyboard.setClipboard(device.userCode);
                status = "代码已复制";
                statusColor = MainScreen.C_OK;
            }).dimensions(x, btnRowY(), 66, 18).build());

            add(Btn.builder(Txt.of("打开浏览器"), b -> openUri(
                    device == null ? "https://www.microsoft.com/link" : device.verifyUri))
                    .dimensions(x + 70, btnRowY(), 78, 18).build());

            add(Btn.builder(Txt.of("取消"), b -> {
                msCancel = true;
                msWaiting = false;
                device = null;
                msStatus = "";
                msError = "";
                host.rebuild();
            }).dimensions(x + 152, btnRowY(), 44, 18).build());
            return;
        }

        // ---- 离线：名字 + 按钮
        nameField = new TextFieldWidget(this.tr(), x + 28, nameRowY(), 108, 16, Txt.of("玩家名"));
        nameField.setMaxLength(16);
        nameField.setText(suggestName());
        add(nameField);

        add(Btn.builder(Txt.of("离线登录"), b -> addOffline())
                .dimensions(x + 140, nameRowY(), 64, 16).build());

        add(Btn.builder(Txt.of("微软登录"), b -> startMicrosoft())
                .dimensions(x, btnRowY(), 78, 18).build());

        boolean hasSel = findSelected() != null;
        add(Btn.builder(Txt.of("设为当前"), b -> {
            Account a = findSelected();
            if (a != null) {
                Config.setAccount(a);
                status = "当前账号: " + a.name;
                statusColor = MainScreen.C_OK;
                host.rebuild();
            }
        }).dimensions(x + 82, btnRowY(), 70, 18).build());

        add(Btn.builder(Txt.of("删除"), b -> removeSelected())
                .dimensions(x + 156, btnRowY(), 44, 18).build());

        // 分页（账号多了用）
        add(Btn.builder(Txt.of("◀"), b -> {
            if (page > 0) { page--; host.rebuild(); }
        }).dimensions(x + w - 72, btnRowY(), 18, 18).build());
        add(Btn.builder(Txt.of("▶"), b -> {
            if (page < listPages() - 1) { page++; host.rebuild(); }
        }).dimensions(x + w - 18, btnRowY(), 18, 18).build());
    }

    private String suggestName() {
        Account a = Config.account();
        if (a != null && !a.isMsa()) return a.name;
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    private Account findSelected() {
        for (Account a : Config.accounts) {
            if (a.name.equals(selectedName)) return a;
        }
        return null;
    }

    // ================================================================ 动作

    private void addOffline() {
        String name = nameField == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            status = "先填个玩家名";
            statusColor = MainScreen.C_ERR;
            return;
        }
        if (!name.matches("[A-Za-z0-9_]{2,16}")) {
            status = "名字只能用字母数字下划线（2-16 位）";
            statusColor = MainScreen.C_ERR;
            return;
        }
        Account a = Account.offline(name);
        Config.accounts.removeIf(x -> x.name.equalsIgnoreCase(a.name) && !x.isMsa());
        Config.setAccount(a);
        selectedName = a.name;
        status = "已添加离线账号 " + name;
        statusColor = MainScreen.C_OK;
        host.rebuild();
    }

    private void removeSelected() {
        Account a = findSelected();
        if (a == null) {
            status = "先选一个账号";
            statusColor = MainScreen.C_ERR;
            return;
        }
        Config.accounts.remove(a);
        Config.accountIndex = Config.accounts.isEmpty() ? -1 : 0;
        selectedName = Config.accounts.isEmpty() ? null : Config.accounts.get(0).name;
        Config.save();
        status = "已删除 " + a.name;
        statusColor = MainScreen.C_DIM;
        host.rebuild();
    }

    private void startMicrosoft() {
        msWaiting = true;
        msCancel = false;
        msError = "";
        msStatus = "正在申请设备码…";
        device = null;
        host.rebuild();

        Thread t = new Thread(() -> {
            try {
                Auth.Device d = Auth.startDevice();
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.execute(() -> {
                    device = d;
                    msStatus = "等待授权";
                    host.rebuild();
                });
                Account a = Auth.awaitLogin(d, s -> mc.execute(() -> msStatus = s),
                        () -> msCancel);
                mc.execute(() -> {
                    msWaiting = false;
                    device = null;
                    Config.accounts.removeIf(x -> x.isMsa() && x.uuid.equals(a.uuid));
                    Config.setAccount(a);
                    selectedName = a.name;
                    status = "已登录 " + a.name;
                    statusColor = MainScreen.C_OK;
                    host.rebuild();
                });
            } catch (Exception e) {
                McVerMod.LOGGER.warn("mcver: 微软登录失败 {}", e.getMessage());
                MinecraftClient.getInstance().execute(() -> {
                    msWaiting = false;
                    device = null;
                    msError = e.getMessage() == null ? e.toString() : e.getMessage();
                    host.rebuild();
                });
            }
        }, "mcver-msa-login");
        t.setDaemon(true);
        t.start();
    }

    private void openUri(String url) {
        try {
            Util.getOperatingSystem().open(URI.create(url));
        } catch (Throwable e) {
            status = "打不开浏览器，手动访问 " + url;
            statusColor = MainScreen.C_WARN;
        }
    }

    // ================================================================ render

    @Override
    public void render(Ctx ctx, int mouseX, int mouseY, float delta) {
        int x = host.contentX();
        int w = host.contentW();

        if (Config.accounts.isEmpty() && !msWaiting) {
            text(ctx, "还没有账号", x, listTop() + 4, MainScreen.C_DIM);
            text(ctx, "底部填名字点「离线登录」，或者用微软账号登录", x, listTop() + 16, MainScreen.C_FAINT);
        }
        if (Config.accounts.size() > listRows()) {
            // 分页指示画在两个箭头中间，不再盖住列表第一行
            centered(ctx, (page + 1) + "/" + listPages(), x + w - 36, btnRowY() + 5, MainScreen.C_FAINT);
        }

        if (msWaiting) {
            text(ctx, "微软登录", x, msTop() - 2, MainScreen.C_TEXT);
            text(ctx, "在浏览器打开 www.microsoft.com/link", x, msTop() + 12, MainScreen.C_DIM);
            if (device == null) {
                text(ctx, msStatus, x, msTop() + 26, MainScreen.C_WARN);
            } else {
                text(ctx, "输入代码:", x, msTop() + 24, MainScreen.C_DIM);
                drawCode(ctx, device.userCode, x + 56, msTop() + 20);
            }
            if (!msStatus.isEmpty() && device != null) {
                rightAligned(ctx, trim(msStatus, 150), x + w, msTop() + 24, MainScreen.C_FAINT);
            }
            if (!msError.isEmpty()) {
                text(ctx, trim(msError, w), x, msTop() + 62, MainScreen.C_ERR);
            }
            return;
        }

        text(ctx, "名字", x, nameRowY() + 4, MainScreen.C_DIM);

        if (!status.isEmpty()) {
            text(ctx, trim(status, w), x, statusY(), statusColor);
        } else {
            text(ctx, "选中的账号会用于启动；正版登录需要联网", x, statusY(), MainScreen.C_FAINT);
        }
        if (!msError.isEmpty()) {
            text(ctx, trim(msError, w), x, statusY() + 12, MainScreen.C_ERR);
        }
    }

    /** 设备码画成一小块深色底板，方便照着输。 */
    private void drawCode(Ctx ctx, String code, int x, int y) {
        int w = this.tr().getWidth(code) + 10;
        ctx.fill(x, y, x + w, y + 12, 0xFF3A3A3A);
        ctx.fill(x, y, x + w, y + 1, 0xFF202020);
        text(ctx, code, x + 5, y + 2, 0xFFFFE08A);
    }
}
