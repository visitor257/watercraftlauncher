package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * 1.21.9 时代的版本差异层（1.21.9 / 1.21.10 / 1.21.11 共用）。和主源码的差别只有键盘这一处。
 *
 * <p>1.21.9 起把 {@code Screen#keyPressed} 从
 * {@code (int keyCode, int scanCode, int modifiers)} 换成了
 * {@code keyPressed(KeyInput)}（{@code KeyInput} 是个 record，有 {@code key()} /
 * {@code scancode()} / {@code modifiers()}）。编译器原文：
 * <pre>
 *   method keyPressed in class Screen cannot be applied to given types;
 *     required: KeyInput
 *     found:    int,int,int
 * </pre>
 * 所以这里把 KeyInput 拆成三个 int，交回 {@link #keyPressedDefault}——
 * UI 层（MainScreen 等）完全不用知道 KeyInput 存在。
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);
    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.21.9 仍有横向参数。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        renderContent(Ctx.of(ctx), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        drawChrome(Ctx.of(ctx));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (onScroll(mouseX, mouseY, horizontal, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /** 1.21.9 唯一的新东西：KeyInput record 拆成三个 int。 */
    @Override
    public boolean keyPressed(KeyInput input) {
        return keyPressedDefault(input.key(), input.scancode(), input.modifiers());
    }

    /** 子类实现这个（签名稳定，所有 target 一样）。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(new KeyInput(keyCode, scanCode, modifiers));
    }

    protected <T extends ClickableWidget> T addWidget(T w) {
        return addDrawableChild(w);
    }

    protected void clearWidgets() {
        clearChildren();
    }

    public void closeSelf() {
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    @Override
    public void close() {
        closeSelf();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
