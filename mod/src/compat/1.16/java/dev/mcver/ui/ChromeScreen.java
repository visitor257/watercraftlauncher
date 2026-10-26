package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * 1.16.5 版差异层（{@code src/main} 那份按 1.21.4 的签名写）。
 *
 * <p>1.16.5 跟 1.18.2 几乎一样，但多两处**命法**差异（钩子名在 1.19 改过）：
 * <ul>
 *   <li>关界面：1.16.5 叫 {@code onClose()}，1.19+ 叫 {@code close()}；</li>
 *   <li>是否暂停：1.16.5 叫 {@code isPauseScreen()}，1.19+ 叫 {@code shouldPause()}；</li>
 *   <li>剩下的 {@code render(MatrixStack, int, int, float)} / {@code renderBackground(MatrixStack)} /
 *       3 参 {@code mouseScrolled} 跟 1.18.2 一致。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 加控件：1.16.5 叫 addChild（1.17+ 才改名 addDrawableChild）。 */
    protected <T extends net.minecraft.client.gui.widget.ClickableWidget> T addWidget(T w) {
        return addChild(w);
    }

    /** 清空控件：1.16.5 没有 clearChildren()，直接清 buttons（protected 字段）。 */
    protected void clearWidgets() {
        buttons.clear();
    }

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.16.5 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // 1.16.5 的 Screen.render 只遍历 drawables，**不会**调 renderBackground
        // （自动调是 1.21.1 才有的行为）。不手动调就会整个面板不画、界面看起来是透明的。
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        renderContent(Ctx.of(matrices), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(MatrixStack matrices) {
        super.renderBackground(matrices);
        drawChrome(Ctx.of(matrices));
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

    /** 键盘：UI 层只 override 这个；1.21.11 的 KeyInput 差异在它自己的 compat 里。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedDefault(keyCode, scanCode, modifiers);
    }
}
