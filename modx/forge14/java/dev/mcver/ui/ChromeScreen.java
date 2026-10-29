package dev.mcver.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本差异层（mojmap 版，MC 1.14.4 / Forge 28.x）。
 *
 * <p>1.14.4 的钩子比 1.16.5 还要再退一步：
 * <ul>
 *   <li>{@code render(int mouseX, int mouseY, float delta)} —— <b>没有 PoseStack</b>（1.15 才引入）；</li>
 *   <li>{@code renderBackground()} 连参数都没有；</li>
 *   <li>滚轮 {@code mouseScrolled(dx, dy, vertical)} 三参；</li>
 *   <li>控件入口 {@code addButton(T)}；{@code clearWidgets()} 不存在 → 自己清两个列表；</li>
 *   <li>{@code isPauseScreen()}。</li>
 * </ul>
 *
 * <p>{@code Screen#render} 同样<b>不会</b>自己调 {@code renderBackground}，面板在这里手动先画。
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

    /** 滚轮：先给当前页；没人接就交回父类。1.14.4 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        renderBackground();
        super.render(mouseX, mouseY, delta);
        renderContent(Ctx.of(), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
        drawChrome(Ctx.of());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (onScroll(mouseX, mouseY, 0.0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    /** 加一个控件。1.14.4 的入口是 {@code addButton}（会同时进 children 和 buttons）。 */
    protected void addControl(Object w) {
        if (w instanceof AbstractWidget) {
            addButton((AbstractWidget) w);
        }
    }

    /** 泛型版：返回原对象，方便链式写法。 */
    protected <T extends AbstractWidget> T add(T w) {
        addControl(w);
        return w;
    }

    /** 清空控件。1.14.4 没有 {@code clearWidgets()}，两个列表都得清。 */
    protected void clearWidgets() {
        this.children.clear();
        this.buttons.clear();
    }

    /** 键盘：子类实现这个，父类接住后转下来。 */
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
