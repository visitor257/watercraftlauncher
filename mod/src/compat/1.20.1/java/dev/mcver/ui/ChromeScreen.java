package dev.mcver.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 1.20.1 兼容层：{@link ChromeScreen} 的旧签名版本。
 *
 * <p>与主源码的差异只有两个方法签名：
 * <ul>
 *   <li>背景渲染在 1.20.1 是 {@code renderBackground(DrawContext)}（1.20.2+ 才带鼠标坐标和 delta）；</li>
 *   <li>滚轮回调在 1.20.1 是 3 个参数（横向滚动 1.20.2+ 才有）。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(DrawContext ctx);

    /** 滚轮：先给当前页；没人接就交回父类。1.20.1 没有横向滚动，horizontal 传 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void renderBackground(DrawContext ctx) {
        super.renderBackground(ctx);
        drawChrome(ctx);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (onScroll(mouseX, mouseY, 0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }
}
