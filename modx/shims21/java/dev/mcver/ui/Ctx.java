package dev.mcver.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * 渲染上下文适配层（mojmap 版，1.20.2+ NeoForge/Forge 通用）。
 *
 * <p>UI 代码只碰三个原语：{@code fill} / {@code drawText} / {@code drawItem}。
 * 在 yarn 源码里这个类包的是 {@code DrawContext}，mojmap 里同一个东西叫
 * {@code GuiGraphics}；三个原语的 mojmap 名字分别是 {@code fill} /
 * {@code drawString} / {@code renderItem}。
 *
 * <p>和 Fabric 版不同的是：这里<b>不需要</b>那套 MethodHandle 兜底。
 * Fabric 的 1.21 系列是一个 jar 通吃 1.21~1.21.11，所以 {@code drawText} 的返回
 * 类型（int→void）只能运行期摸；NeoForge/Forge 每个 jar 只对准一个 MC 版本编译，
 * 编译期就能定死签名。
 */
public final class Ctx {

    private final GuiGraphics inner;

    private Ctx(GuiGraphics inner) {
        this.inner = inner;
    }

    public static Ctx of(GuiGraphics ctx) {
        return new Ctx(ctx);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        inner.fill(x1, y1, x2, y2, color);
    }

    public void drawText(Font font, Component t, int x, int y, int color, boolean shadow) {
        inner.drawString(font, t, x, y, color, shadow);
    }

    /** 物品图标（左上角那颗下载工具 / 自检用的绿块）。 */
    public void drawItem(ItemStack stack, int x, int y) {
        inner.renderItem(stack, x, y);
    }
}
