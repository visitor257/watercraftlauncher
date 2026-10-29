package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 1.7.10 绘制适配层（Legacy Fabric 最老的一段）。
 *
 * <p>和三处不同：
 * <ul>
 *   <li>{@code TextRenderer#draw(String, int, int, int, boolean)} —— 坐标是 <b>int</b>（1.8 才变 float）；</li>
 *   <li>{@code Text} 没有 {@code getString()}，只有 {@code asUnformattedString()} / {@code asFormattedString()}
 *       —— 这里要的是不带 {@code §} 的纯文本，用前者；</li>
 *   <li>画物品：1.7 的 {@code ItemRenderer} 是 {@code Screen} 上的 <b>protected static 字段</b>，
 *       而且方法要自己传 TextRenderer + TextureManager：
 *       {@code method_1549(tr, textureManager, stack, x, y)}。字段是 protected，
 *       只能由 {@code Screen} 的子类访问 → 走 {@link ChromeScreen#itemRendererOf()}。</li>
 * </ul>
 */
public final class Ctx {

    public static Ctx of() {
        return new Ctx();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        // 1.7.2 的 yarn 没给 DrawableHelper.fill 起名（1.7.10 有）
        DrawableHelper.method_21878(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        tr.draw(t.asUnformattedString(), x, y, color, shadow);
    }

    public void drawItem(ItemStack stack, int x, int y) {
        net.minecraft.client.render.item.ItemRenderer r = ChromeScreen.itemRendererOf();
        if (r == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        r.method_1549(mc.textRenderer, mc.getTextureManager(), stack, x, y);
    }
}
