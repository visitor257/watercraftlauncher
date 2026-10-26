package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 1.20.1 版差异层（{@code src/main} 那份按 1.21.4 的签名写）。
 *
 * <p>1.20.1 已经有 {@link DrawContext}，但两个钩子跟 1.21.4 不同：
 * <ul>
 *   <li>背景渲染是 {@code renderBackground(DrawContext)}（1.21.2 才加 mouseX/mouseY/delta）；</li>
 *   <li>滚轮回调只有 3 个参数（横向滚动 1.20.2 才有）。</li>
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

    /** 加一个控件（各版本钩子名不同，统一走这里；见 1.16.5 那份）。 */
    protected <T extends net.minecraft.client.gui.widget.ClickableWidget> T addWidget(T w) {
        return addDrawableChild(w);
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        clearChildren();
    }

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.20.1 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 1.20.1 的 Screen.render 只遍历 drawables，**不会**调 renderBackground（1.21.1 才改成自动调）。
        // 所以这里必须手动按顺序来：原版底 → 面板 → 控件 → 页内容。
        // 2026-10-26 用户报「面板变透明」就是这个原因：drawChrome 写在 renderBackground 里，
        // 而这个版本根本没人调 renderBackground。
        renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        renderContent(Ctx.of(ctx), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext ctx) {
        super.renderBackground(ctx);
        drawChrome(Ctx.of(ctx));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (onScroll(mouseX, mouseY, 0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    /** 关掉自己并回父屏（UI 代码统一调这个，不碰版本钩子名）。 */
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
