package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.14 系列（1.14 ~ 1.14.4）绘制适配层。
 *
 * <p>比 1.15 还少两样东西：
 * <ul>
 *   <li>{@code TextRenderer} 在 1.14 <b>没有</b>那个收 {@code Matrix4f} + {@code VertexConsumerProvider}
 *       的长重载（1.15 才加），只有 {@code draw(String,int,int,int)} / {@code drawWithShadow(...)}
 *       —— 屏幕空间本来就用即时渲染，正好够用；</li>
 *   <li>{@code MinecraftClient#getBufferBuilders()} 是 1.15 才有的，这里用不到。</li>
 * </ul>
 * {@code DrawableHelper#fill} 仍是 5 参静态版本，{@code ItemRenderer#renderGuiItem} 也还在。
 */
public final class Ctx {

    public static Ctx of() {
        return new Ctx();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        String s = t.getString();
        if (shadow) {
            tr.drawWithShadow(s, (float) x, (float) y, color);
        } else {
            tr.draw(s, (float) x, (float) y, color);
        }
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
    }
}
