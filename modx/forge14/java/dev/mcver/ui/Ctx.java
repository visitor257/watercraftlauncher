package dev.mcver.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染上下文适配层（mojmap 版，MC 1.14.4 / Forge 28.x）。
 *
 * <p><b>1.14.4 是「PoseStack 之前」那一代</b>：没有 {@code PoseStack}、没有 {@code GuiGraphics}，
 * 所有绘制坐标都是裸 int，{@code Screen#render} 也只有 {@code (int mouseX, int mouseY, float delta)}。
 * 所以这个类不带任何状态 —— {@code Ctx.of()} 只是为了让 UI 代码的签名保持不变。
 *
 * <ul>
 *   <li>{@code fill} → {@code GuiComponent.fill(int,int,int,int,int)}（静态）；</li>
 *   <li>{@code drawText} → {@code Font#drawShadow(String,...)} —— 1.14.4 的 Font <b>没有</b>
 *       {@code Component} 重载（1.15 才加），要自己 {@code getString()}；</li>
 *   <li>{@code drawItem} → {@code ItemRenderer#renderAndDecorateItem(stack, x, y)}。</li>
 * </ul>
 */
public final class Ctx {

    private static final Ctx INSTANCE = new Ctx();

    private Ctx() { }

    /** 1.14.4 没有渲染上下文对象，传什么都一样。 */
    public static Ctx of() {
        return INSTANCE;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(x1, y1, x2, y2, color);
    }

    public void drawText(Font font, Component t, int x, int y, int color, boolean shadow) {
        String s = t == null ? "" : t.getString();
        if (shadow) {
            font.drawShadow(s, (float) x, (float) y, color);
        } else {
            font.draw(s, (float) x, (float) y, color);
        }
    }

    /** 物品图标（左上角那颗下载工具 / 自检用的绿块）。 */
    public void drawItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
    }
}
