package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 1.15 版差异层（{@code src/main} 那份是按 1.20+ 写的）。
 *
 * <p>1.15 与 1.16.5 的差异（都是硬性的）：
 * <ul>
 *   <li>{@code render(int mouseX, int mouseY, float delta)} —— <b>还没有 MatrixStack</b>；</li>
 *   <li>{@code renderBackground()} 无参（1.16 起收 MatrixStack）；</li>
 *   <li>加控件叫 {@code addButton(...)}，收 {@code AbstractButtonWidget}；</li>
 *   <li>{@code Screen} 里的字体字段叫 {@code font}（1.16 改名为 {@code textRenderer}），
 *       子类页面代码里写的是 {@code textRenderer}，所以这里补一个同名字段并在渲染前同步；</li>
 *   <li>清控件要同时清 {@code buttons} 和 {@code children} 两个列表。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时记。 */
    protected Screen parentScreen;

    /** 1.15 的 {@code Screen#font} 在 1.16 改名 textRenderer；页面代码按新名字写，这里同步一份。 */
    protected TextRenderer textRenderer;

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分割线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 加控件：1.15 只有 {@code addButton(AbstractButtonWidget)}。 */
    protected void addWidget(Object w) {
        addButton((net.minecraft.client.gui.widget.AbstractButtonWidget) w);
    }

    /** 清空控件：1.15 没有 clearChildren()，两个列表都要清。 */
    protected void clearWidgets() {
        buttons.clear();
        children.clear();
    }

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.15 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        // 1.15 的 Screen.render 只遍历 drawables，不会自己调 renderBackground，
        // 不手动调就会整块面板不画、界面看起来是透明的（和 1.16~1.20 一个坑）。
        this.textRenderer = this.font;
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
        if (onScroll(mouseX, mouseY, 0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    /** 关掉自己并回父屏（UI 代码统一调这个，不碰版本钩子名）。 */
    public void closeSelf() {
        MinecraftClient.getInstance().openScreen(parentScreen);
    }

    @Override
    public void onClose() {
        closeSelf();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 键盘：UI 层只 override 这个。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedDefault(keyCode, scanCode, modifiers);
    }
}
