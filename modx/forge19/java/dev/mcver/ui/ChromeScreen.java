package dev.mcver.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本差异层（mojmap 版，PoseStack 时代：MC 1.19.4 / Forge 45.x）。
 *
 * <p>1.19.4 的三个钩子：
 * <ul>
 *   <li>{@code render(PoseStack, int, int, float)}；</li>
 *   <li>{@code renderBackground(PoseStack)} 只有 pose 一个参数；</li>
 *   <li>{@code mouseScrolled(double, double, double)} 只有 3 个参数（横向滚动 1.20.2 才加）；</li>
 *   <li>暂停判定叫 {@code isPauseScreen()}（mojmap 到 1.20 才改名 shouldPause）。</li>
 * </ul>
 *
 * <p>和 1.20.1 那份一样：{@code Screen#render} <b>不会</b>自己调 {@code renderBackground}，
 * 面板必须在这里手动先画，否则整个背景板是透明的。
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

    /** 滚轮：先给当前页；没人接就交回父类。1.19.4 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float delta) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, delta);
        renderContent(Ctx.of(pose), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(PoseStack pose) {
        super.renderBackground(pose);
        drawChrome(Ctx.of(pose));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (onScroll(mouseX, mouseY, 0.0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    /** 加一个控件（别名 {@code addRenderableWidget}）；参数收宽到 Object，见 1.20.1 那份的说明。 */
    protected void addWidget(Object w) {
        if (w instanceof AbstractWidget aw) {
            addRenderableWidget(aw);
        }
    }

    /** 泛型版：返回原对象，方便链式写法。 */
    protected <T extends AbstractWidget> T add(T w) {
        addWidget(w);
        return w;
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        super.clearWidgets();
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
