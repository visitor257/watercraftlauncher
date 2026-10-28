package dev.mcver.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染上下文适配层（mojmap 版，MC 1.18.2）。
 *
 * <p>UI 代码只碰三个原语：{@code fill} / {@code drawText} / {@code drawItem}。
 * 1.19.4 还没有 {@code GuiGraphics}（1.20 才引入），所有绘制都要自己拿 {@link PoseStack}：
 * <ul>
 *   <li>{@code fill} → {@code GuiComponent.fill(pose, ...)}（静态方法，Screen 不继承 GuiComponent）；</li>
 *   <li>{@code drawText} → {@code Font#draw}/{@code drawShadow}（1.19.4 是 boolean-in-batch 那套，
 *       带不带阴影是两个方法，不是参数）；</li>
 *   <li>{@code drawItem} → {@code ItemRenderer#renderAndDecorateItem(stack, x, y)}：
 *       <b>1.18 起这个方法不再收 PoseStack</b>（1.18 把矩阵改成 RenderSystem 全局状态），
 *       但 fill / 文字仍然要 pose。所以这个类里 pose 只给前两个原语用。</li>
 * </ul>
 *
 * <p>和 Fabric 那份 1.19.3 的区别：那边一个 jar 要同时盖 1.19.3/1.19.4，签名漂移只能走
 * MethodHandle；这里每个 jar 只对准一个 MC 版本编，直接调就行。
 */
public final class Ctx {

    private final PoseStack pose;

    private Ctx(PoseStack pose) {
        this.pose = pose;
    }

    public static Ctx of(PoseStack pose) {
        return new Ctx(pose);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public void drawText(Font font, Component t, int x, int y, int color, boolean shadow) {
        if (shadow) {
            font.drawShadow(pose, t, (float) x, (float) y, color);
        } else {
            font.draw(pose, t, (float) x, (float) y, color);
        }
    }

    /** 物品图标（左上角那颗下载工具 / 自检用的绿块）。 */
    public void drawItem(ItemStack stack, int x, int y) {
        // 1.18.2 没有 pose 参数（1.19 才加回去，1.20 又换成 GuiGraphics）
        Minecraft.getInstance().getItemRenderer().renderAndDecorateItem(stack, x, y);
    }
}
