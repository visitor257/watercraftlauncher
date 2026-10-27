package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.13.2 绘制适配层（Legacy Fabric 段）。
 *
 * <p>和 1.14 那份几乎一样，差别只有两处：
 * <ul>
 *   <li>{@code TextRenderer} 还是 1.13 的双方法：{@code drawWithShadow(String,float,float,int)}
 *       和 {@code draw(...)}（yarn 没给名字，还是 {@code method_18355}）；</li>
 *   <li><b>{@code ItemRenderer} 这个类在 1.13.2 还不存在</b>——GUI 画物品这时挂在
 *       {@code HeldItemRenderer}（就是老 ItemRenderer 改名）上：
 *       {@code MinecraftClient#getHeldItemRenderer().method_19376(stack, x, y)}。
 *       别去找 {@code net.minecraft.client.render.item.ItemRenderer}，它确实没有。</li>
 * </ul>
 * {@code DrawableHelper#fill} 仍是 5 参静态版本。
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
            tr.method_18355(s, (float) x, (float) y, color);
        }
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getHeldItemRenderer().method_19376(stack, x, y);
    }
}
