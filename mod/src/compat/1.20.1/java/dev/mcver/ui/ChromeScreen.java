package dev.mcver.ui;

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

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.20.1 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
}
