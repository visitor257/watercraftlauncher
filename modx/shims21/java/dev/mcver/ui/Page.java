package dev.mcver.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** 主面板里的一页（mojmap 版）。控件由页自己往 Screen 上加。 */
public abstract class Page {

    protected final MainScreen host;

    protected Page(MainScreen host) {
        this.host = host;
    }

    /** 左侧导航上的字。 */
    public abstract String nav();

    /** 加控件。切换页时 MainScreen 会清空再调它。 */
    public abstract void init();

    /** 重加控件（切页 / 状态变化时用）。 */
    protected void rebuild() {
        host.rebuild();
    }

    /** 往当前 Screen 上加控件。 */
    protected <T extends AbstractWidget> T add(T w) {
        return host.add(w);
    }

    public void render(Ctx ctx, int mouseX, int mouseY, float delta) { }

    /** 每 tick 一次（只有当前页会收到）；状态变了想刷按钮就在这里做。 */
    public void tick() { }

    public void onLeave() { }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return false;
    }

    // ================================================================ 小工具

    protected Font tr() {
        return Minecraft.getInstance().font;
    }

    protected void text(Ctx ctx, String s, int x, int y, int color) {
        ctx.drawText(tr(), Txt.of(s), x, y, color, false);
    }

    protected void text(Ctx ctx, Component t, int x, int y, int color) {
        ctx.drawText(tr(), t, x, y, color, false);
    }

    protected void centered(Ctx ctx, String s, int cx, int y, int color) {
        ctx.drawText(tr(), Txt.of(s), cx - tr().width(s) / 2, y, color, false);
    }

    protected void rightAligned(Ctx ctx, String s, int rx, int y, int color) {
        ctx.drawText(tr(), Txt.of(s), rx - tr().width(s), y, color, false);
    }

    protected String trim(String s, int w) {
        return tr().plainSubstrByWidth(s, Math.max(8, w));
    }

    protected static String env(String key) {
        String v = System.getenv(key);
        return v == null || v.trim().isEmpty() ? null : v.trim();
    }

    protected static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    /** 一行的灰条底，让列表和按钮区有分隔感。 */
    protected void line(Ctx ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + 1, 0xFF8B8B8B);
        ctx.fill(x, y + 1, x + w, y + 2, 0xFFFFFFFF);
    }
}
