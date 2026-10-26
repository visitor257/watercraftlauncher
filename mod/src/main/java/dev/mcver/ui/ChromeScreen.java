package dev.mcver.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 版本差异层：{@link Screen} 里跨版本改过签名的两个钩子集中放这儿。
 *
 * <ul>
 *   <li>背景渲染：1.21.4 是 {@code renderBackground(DrawContext, int, int, float)}，
 *       1.20.1 是 {@code renderBackground(DrawContext)}；</li>
 *   <li>滚轮回调：1.20.2+ 才有横向参数（4 个参数），1.20.1 只有 3 个。</li>
 * </ul>
 *
 * 主源码按 1.21.4 写；旧 target 在 {@code src/compat/<target>/java} 里整文件覆盖这个类，
 * 这样 300 行的 MainScreen 不用跟着复制一份。
 */
public abstract class ChromeScreen extends Screen {

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(DrawContext ctx);

    /** 滚轮：先给当前页；没人接就交回父类。旧版本没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        drawChrome(ctx);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (onScroll(mouseX, mouseY, horizontal, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
}
