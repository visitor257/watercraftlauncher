package dev.mcver.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染上下文适配层（MC 1.12.2）。
 *
 * <p>1.12.2 的绘制是纯静态调用，没有任何上下文对象：
 * <ul>
 *   <li>{@code Gui.func_73734_a(x1,y1,x2,y2,argb)} 填矩形；</li>
 *   <li>{@code FontRenderer.func_175063_a} 带阴影、{@code func_78276_b} 不带；</li>
 *   <li>{@code RenderItem.func_175042_a(stack,x,y)} 画物品图标。</li>
 * </ul>
 * 所以 {@link #of()} 不带参数，返回一个无状态单例 —— 与 1.14.4 那份同构。
 */
public final class Ctx {

    private static final Ctx INSTANCE = new Ctx();

    private Ctx() { }

    public static Ctx of() {
        return INSTANCE;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        Gui.func_73734_a(x1, y1, x2, y2, color);
    }

    public void drawText(Font font, Component t, int x, int y, int color, boolean shadow) {
        String s = t == null ? "" : t.getString();
        if (shadow) {
            font.unwrap().func_175063_a(s, (float) x, (float) y, color);
        } else {
            font.unwrap().func_78276_b(s, x, y, color);
        }
    }

    public void drawItem(ItemStack stack, int x, int y) {
        net.minecraft.client.Minecraft.func_71410_x().func_175599_af().func_175042_a(stack.unwrap(), x, y);
    }
}
