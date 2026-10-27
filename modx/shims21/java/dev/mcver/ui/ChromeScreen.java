package dev.mcver.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本/loader 差异层（mojmap 版，对准 MC 1.21.1 / NeoForge 21.1）。
 *
 * <p>和 Fabric 版的区别只在命名：
 * <ul>
 *   <li>{@code Screen#render(GuiGraphics,int,int,float)}（yarn: {@code DrawContext}）；</li>
 *   <li>{@code Screen#close()} → {@code onClose()}；{@code shouldPause()} → {@code isPauseScreen()}；</li>
 *   <li>{@code addDrawableChild} → {@code addRenderableWidget}；{@code clearChildren} → {@code clearWidgets()}；</li>
 *   <li>1.21.1 的 {@code Screen#render} 自己会调 {@code renderBackground}，所以面板在那边画。</li>
 * </ul>
 *
 * <p>页面代码只见 {@link Ctx}，不出现 {@code GuiGraphics}；钩子签名一变只改本文件。
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    protected ChromeScreen(Component title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // super.render 里会先调 renderBackground（下面那个 override）把面板画出来，
        // 再画 addRenderableWidget 加进来的按钮。
        super.render(ctx, mouseX, mouseY, delta);
        renderContent(Ctx.of(ctx), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        drawChrome(Ctx.of(ctx));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (onScroll(mouseX, mouseY, horizontal, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** 加一个控件。别名：{@code addRenderableWidget}。 */
    protected <T extends AbstractWidget> T addWidget(T w) {
        addRenderableWidget(w);
        return w;
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        clearWidgets0();
    }

    private void clearWidgets0() {
        super.clearWidgets();
    }

    /**
     * 键盘：子类实现这个。1.21.1 的签名还是 {@code keyPressed(int,int,int)}，
     * 所以这里可以直接接住并转给子类。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyPressedDefault(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 关掉自己并回父屏。业务代码统一调这个。 */
    public void closeSelf() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void onClose() {
        closeSelf();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
