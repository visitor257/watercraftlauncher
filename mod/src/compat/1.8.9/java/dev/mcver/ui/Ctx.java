package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.12.2（Legacy Fabric）绘制适配层。
 *
 * <p>和 1.15 的差别：
 * <ul>
 *   <li>{@code DrawableHelper#fill(int×5)} 一样是静态、不收矩阵；</li>
 *   <li>画字只有 {@code TextRenderer#draw(String, float, float, int, boolean)} /
 *       {@code drawWithShadow(String,float,float,int)} —— <b>没有 Matrix4f 重载</b>；</li>
 *   <li>{@code Text} 取字符串是 {@code asFormattedString()}（不是 1.16+ 的 {@code getString()}）；</li>
 *   <li>物品图标是 {@code ItemRenderer#method_12455(ItemStack,int,int)}
 *       （yarn 没给这个名字，obf 就是 renderItemAndEffectIntoGUI）。</li>
 * </ul>
 */
public final class Ctx {

    public static Ctx of() {
        return new Ctx();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        tr.draw(t.asFormattedString(), (float) x, (float) y, color, shadow);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        MinecraftClient.getInstance().getItemRenderer().renderInGuiWithOverrides(stack, x, y);
    }
}
