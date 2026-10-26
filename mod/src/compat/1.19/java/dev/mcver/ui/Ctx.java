package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.19 ~ 1.19.2 渲染上下文适配层（1.19.3/1.19.4 那份见 compat/1.19.3）。
 *
 * <p>跟 1.19.3 的差别只有两处（都是"1.19.4 加了个参数"）：
 * <ul>
 *   <li>{@code TextRenderer#draw} 第 8 参是 {@code boolean seeThrough}（1.19.4 换成
 *       {@code TextLayerType} 枚举）；</li>
 *   <li>{@code ItemRenderer#renderInGui(ItemStack, int, int)} <b>不收 MatrixStack</b>
 *       （1.19.4 才有收矩阵的重载），内部直接用 RenderSystem 的模型矩阵 ——
 *       屏幕渲染时那个矩阵就是单位矩阵，坐标不用换算。</li>
 * </ul>
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
                mc.getBufferBuilders().getEntityVertexConsumers(), false, 0, 0xF000F0);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderInGui(stack, x, y);
    }
}
