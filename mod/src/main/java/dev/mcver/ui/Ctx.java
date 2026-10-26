package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 渲染上下文适配层。
 *
 * <p>UI 代码只碰三个原语：{@code fill} / {@code drawText} / {@code drawItem}。这三个
 * 在 1.20 之前根本没有 {@code net.minecraft.client.gui.DrawContext} 这个类型
 * （1.19.4 及更早是 {@code MatrixStack} + {@code VertexConsumerProvider}），
 * 所以把所有绘制都收口到这一个类，页面代码就跟 MC 版本无关了。
 *
 * <p>每个 target 一份实现：
 * <ul>
 *   <li>1.20+（本文件）：薄包一层 {@link DrawContext}；</li>
 *   <li>1.19.4 及更早（{@code src/compat/<target>/java}）：包 MatrixStack。</li>
 * </ul>
 */
public final class Ctx {

    private final DrawContext inner;

    private Ctx(DrawContext inner) {
        this.inner = inner;
    }

    public static Ctx of(DrawContext ctx) {
        return new Ctx(ctx);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        inner.fill(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        inner.drawText(tr, t, x, y, color, shadow);
    }

    /** 物品图标（左上角那颗下载工具 / 自检用的绿块）。1.19.4 走 ItemRenderer#renderInGui。 */
    public void drawItem(ItemStack stack, int x, int y) {
        inner.drawItem(stack, x, y);
    }
}
