package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.18.2 版渲染上下文适配层（{@code src/main} 那份是给 1.20+ 的 DrawContext 用的）。
 *
 * <p>1.18.2 比 1.19.4 又早一步，三处细节都不一样：
 * <ul>
 *   <li>{@code fill} → {@link DrawableHelper#fill} 静态方法，跟 1.19.4 一样；</li>
 *   <li>{@code drawText} → {@link TextRenderer#draw} 的第 8 个参数在 1.18.2 还是
 *       {@code boolean seeThrough}（1.19.4 换成了 {@code TextLayerType} 枚举）；</li>
 *   <li>{@code drawItem} → {@code ItemRenderer#renderInGui(ItemStack, int, int)}
 *       <b>不收 MatrixStack</b>（1.19.4 才有收矩阵的重载），它内部直接用 RenderSystem
 *       的模型矩阵。屏幕渲染时那个矩阵就是单位矩阵，所以坐标不用额外换算。</li>
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
