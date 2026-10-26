package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.19.4 版渲染上下文适配层（{@code src/main} 那份是给 1.20+ 的 DrawContext 用的）。
 *
 * <p>1.19.4 还没有 {@code net.minecraft.client.gui.DrawContext}（1.20 才引入），
 * 绘制要自己拿 {@link MatrixStack}：
 * <ul>
 *   <li>{@code fill} → {@link DrawableHelper#fill} 静态方法；</li>
 *   <li>{@code drawText} → {@link TextRenderer#draw}，注意它收的是 {@code Matrix4f}
 *       （不是 MatrixStack），要 {@code matrices.peek().getPositionMatrix()}；</li>
 *   <li>{@code drawItem} → {@code ItemRenderer#renderInGui(MatrixStack, ItemStack, int, int)}
 *       （1.20+ 的 {@code DrawContext#drawItem} 前身）。</li>
 * </ul>
 *
 * <p>顶点缓冲用 {@code getBufferBuilders().getEntityVertexConsumers()}：跟原版按钮同一套，
 * {@code TextRenderer#draw} 收到 Immediate 会自己 flush，不用额外处理。
 */
public final class Ctx {

    private final MatrixStack matrices;

    private Ctx(MatrixStack matrices) {
        this.matrices = matrices;
    }

    public static Ctx of(MatrixStack matrices) {
        return new Ctx(matrices);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        tr.draw(t, x, y, color, shadow, matrices.peek().getPositionMatrix(),
                mc.getBufferBuilders().getEntityVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderInGui(matrices, stack, x, y);
    }
}
