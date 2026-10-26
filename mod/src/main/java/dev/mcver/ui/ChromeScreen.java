package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 版本差异层：{@link Screen} 里跨版本改过签名的几个钩子集中放这儿。
 *
 * <ul>
 *   <li>主渲染：1.20+ 是 {@code render(DrawContext, int, int, float)}，
 *       1.19.4 及更早是 {@code render(MatrixStack, int, int, float)}；</li>
 *   <li>背景渲染：1.21.4 是 {@code renderBackground(DrawContext, int, int, float)}，
 *       1.20.x 是 {@code renderBackground(DrawContext)}，
 *       1.19.4 是 {@code renderBackground(MatrixStack, int, int, float)}；</li>
 *   <li>滚轮回调：1.20.2+ 才有横向参数（4 个参数），更早只有 3 个。</li>
 * </ul>
 *
 * <p>这几个 override 全部收在本类里，子类只实现 {@link #drawChrome(Ctx)}（背景与面板）
 * 和 {@link #renderContent}（页内容），两边都不出现 MC 的版本类型 —— 否则 rename/MatrixStack
 * 一变，300 行的 MainScreen 就得每个 target 复制一份。
 *
 * <p>旧 target 在 {@code src/compat/<target>/java} 里整文件覆盖这个类。
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。1.17.1 的钩子名是 onClose()，所以这两个 override 也收在本类。 */
    protected Screen parentScreen;

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。旧版本没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // super.render 里会先调 renderBackground（下面那个 override）把面板画出来，
        // 再画 addDrawableChild 加进来的按钮。
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

    /**
     * 关掉自己并回父屏。UI 代码统一调这个：游戏里的钩子名跨版本改过
     * （1.17.1 {@code onClose} → 1.19+ {@code close}），但业务代码不该跟着改。
     */
    public void closeSelf() {
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    /** 1.19+ 叫 close()（1.17.1 叫 onClose()，在 compat 里覆盖）。 */
    @Override
    public void close() {
        closeSelf();
    }

    /** 1.19+ 叫 shouldPause()（1.17.1 叫 isPauseScreen()，在 compat 里覆盖）。 */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
