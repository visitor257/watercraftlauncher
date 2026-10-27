package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.Matrix4f;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.15 系列（1.15 ~ 1.15.2）绘制适配层。
 *
 * <p>1.15 比 1.16 <b>还要早一步</b>，三个签名都不一样：
 * <ul>
 *   <li>{@code DrawableHelper#fill} 是<b>纯静态、不收矩阵</b>的 5 参版本
 *       （{@code fill(int,int,int,int,int)}；1.16 才改成收 {@code MatrixStack}）；</li>
 *   <li>{@code TextRenderer#draw(String, int, int, int, boolean, Matrix4f, ...)}
 *       收的是 {@link Matrix4f} 而不是 {@code MatrixStack}，屏幕空间直接给单位矩阵；</li>
 *   <li>物品图标叫 {@code ItemRenderer#renderGuiItem(ItemStack, int, int)}
 *       （1.16 改名为 {@code renderInGui}）。</li>
 * </ul>
 *
 * <p>因为 {@code Screen#render} 在 1.15 还没有 {@code MatrixStack} 参数，
 * 这个 target 的 {@link #of()} 不带参数 —— {@link ChromeScreen} 是每个 target 一份，所以一致。
 */
public final class Ctx {

    /** 屏幕空间的绘制矩阵就是单位矩阵（1.15 里 fill/draw 都直接吃 Matrix4f）。 */
    private static final Matrix4f IDENTITY = new Matrix4f();

    public static Ctx of() {
        return new Ctx();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // 走 String 重载：1.15 的 Text 还没有 getString() 之外的稳定重载面。
        tr.draw(t.getString(), (float) x, (float) y, color, shadow, IDENTITY,
                mc.getBufferBuilders().getEntityVertexConsumers(), false, 0, 0xF000F0);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
    }
}
