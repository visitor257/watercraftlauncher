package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.16 系列（1.16 ~ 1.16.5）渲染上下文适配层（{@code src/main} 那份是给 1.20+ 的 DrawContext 用的）。
 *
 * <p>比 1.19.4 又早一步，三处细节不同：
 * <ul>
 *   <li>{@code fill} → {@link DrawableHelper#fill} 静态方法，跟 1.19.4 一样（全系列描述符一致）；</li>
 *   <li>{@code drawText} → {@link TextRenderer#draw} 的第 8 个参数在 1.16 还是
 *       {@code boolean seeThrough}（1.19.4 换成了 {@code TextLayerType} 枚举），
 *       矩阵要取 {@code MatrixStack.Entry#getModel()}（1.18 才改名 getPositionMatrix）；</li>
 *   <li>{@code drawItem} → {@code ItemRenderer#renderInGui(ItemStack, int, int)}
 *       <b>不收 MatrixStack</b>，它内部直接用 RenderSystem 的模型矩阵（屏幕渲染时是单位矩阵）。</li>
 * </ul>
 *
 * <p><b>为什么参数传 {@code String} 而不是 {@code Text}</b>（这个坑让 1.16 / 1.16.1 直接崩）：
 * {@code Text}（{@code class_2561}）的重载是 <b>1.16.2 才加</b>的，1.16 / 1.16.1 只有
 * {@code String} 和 {@code class_5348}（StringVisitable）两套。而我们编译基准是 1.16.5，
 * 编译器会毫不犹豫地选 {@code Text} 那个 → 老版本运行时 {@code NoSuchMethodError}。
 * 用 {@code getString()} 走 {@code String} 重载：这个描述符 1.16~1.16.5 一字不差。
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
        // 必须走 String 重载：Text 重载是 1.16.2 才有的（见类注释）。
        // 1.16 的 MatrixStack.Entry 还叫 getModel()（1.18 才改名 getPositionMatrix）。
        tr.draw(t.getString(), x, y, color, shadow, matrices.peek().getModel(),
                mc.getBufferBuilders().getEntityVertexConsumers(), false, 0, 0xF000F0);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderInGui(stack, x, y);
    }
}
