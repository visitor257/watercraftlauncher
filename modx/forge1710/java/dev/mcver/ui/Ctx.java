package dev.mcver.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染上下文适配层（MC 1.7.10）。
 *
 * <p>1.7.10 的绘制同样是纯静态调用，但比 1.12.2 还老一代：
 * <ul>
 *   <li>{@code Gui.func_73734_a(x1,y1,x2,y2,argb)} 填矩形（这个到 1.12.2 都没变）；</li>
 *   <li>文字颜色是 <b>int 坐标</b>：{@code func_78276_b(s,x,y,color)} 带阴影、
 *       {@code func_78261_a} 不带（1.12.2 的 {@code func_175063_a} 是 float 坐标）；</li>
 *   <li>物品图标要自己 new 一个 {@code RenderItem}（1.7.10 没有 {@code Minecraft#getItemRenderer()}），
 *       并且必须自己开/关 GUI 光照，否则图标是黑的。</li>
 * </ul>
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
            font.unwrap().func_78276_b(s, x, y, color);
        } else {
            font.unwrap().func_78261_a(s, x, y, color);
        }
    }

    public void drawItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.func_71410_x();
        net.minecraft.client.renderer.entity.RenderItem ri = new net.minecraft.client.renderer.entity.RenderItem();
        ri.field_77023_b = 200.0F; // zLevel
        net.minecraft.client.renderer.RenderHelper.func_74520_c(); // GUI 标准光照
        try {
            ri.func_82406_b(mc.field_71466_p, mc.func_110434_K(), stack.unwrap(), x, y);
        } finally {
            net.minecraft.client.renderer.RenderHelper.func_74518_a();
            // 早年的渲染会留下 GL 状态，不复位会把后面的文字/矩形染成怪颜色
            org.lwjgl.opengl.GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_LIGHTING);
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        }
    }
}
