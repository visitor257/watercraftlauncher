package dev.mcver.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本差异层（mojmap 版，MC 1.16.5 / Forge 36.x）。
 *
 * <p>和 1.17.1 那份只差三个**控件管理** API（渲染钩子完全一样：PoseStack + 单参
 * {@code renderBackground} + 三参 {@code mouseScrolled} + {@code isPauseScreen}）：
 * <ul>
 *   <li>{@code addRenderableWidget} 是 1.17 才有的；1.16.5 只有 {@code addButton(T)}；</li>
 *   <li>{@code clearWidgets()} 也是 1.17 才有的；1.16.5 得自己清 {@code children} + {@code buttons}
 *       两个列表（{@code addButton} 会同时往两处塞）；</li>
 *   <li>滚轮走 {@code GuiEventListener#mouseScrolled(dx, dy, vertical)}，只有 3 个参数。</li>
 * </ul>
 *
 * <p>同样：{@code Screen#render} <b>不会</b>自己调 {@code renderBackground}，面板在这里手动先画。
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

    /** 滚轮：先给当前页；没人接就交回父类。1.16.5 没有横向滚动，horizontal 恒为 0。 */
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

    /** 加一个控件。1.16.5 的入口是 {@code addButton}（会同时进 children 和 buttons）。 */
    protected void addWidget(Object w) {
        if (w instanceof AbstractWidget) {
            addButton((AbstractWidget) w);
        }
    }

    /** 泛型版：返回原对象，方便链式写法。 */
    protected <T extends AbstractWidget> T add(T w) {
        addWidget(w);
        return w;
    }

    /** 清空控件。1.16.5 没有 {@code clearWidgets()}，两个列表都得清。 */
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
