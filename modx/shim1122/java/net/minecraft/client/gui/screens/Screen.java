package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 1.12.2 垫片：{@code GuiScreen} + 1.14 时代字段名。
 *
 * <p>为什么需要这层：共享页面代码写的是 1.14+ 的 {@code Screen}（有 {@code width} /
 * {@code height} / {@code font} 字段、{@code init()} / {@code tick()} 钩子）。1.12.2 里
 * 对应的是 {@code field_146294_l} / {@code field_146295_m} / {@code field_146289_q}、
 * {@code func_73866_w_()} / {@code func_73876_c()}。
 *
 * <p>{@code net.minecraft.client.gui.screens} 包在 1.12.2 里不存在（那时叫
 * {@code net.minecraft.client.gui.GuiScreen}），所以不会撞名。
 */
public class Screen extends net.minecraft.client.gui.GuiScreen {

    /** 1.14+ 的 {@code Screen#font}。 */
    protected Font font;

    /** 1.14+ 的 {@code Screen#width} / {@code height}（1.12.2 是 field_146294_l/m）。 */
    public int width;
    public int height;

    protected Screen(Component title) {
        // 1.12.2 的 GuiScreen 没有标题概念
    }

    /** 1.14+ 的 {@code init()}：1.12.2 的对应钩子是 {@code func_73866_w_()}。 */
    protected void init() { }

    /** 1.14+ 的 {@code tick()}：1.12.2 的对应钩子是 {@code func_73876_c()}。 */
    public void tick() { }

    /** 1.14+ 的 {@code shouldCloseOnEsc()}。 */
    protected boolean shouldCloseOnEsc() {
        return true;
    }

    /** 1.14+ 的 {@code Screen#children()}：1.12.2 只有 {@code field_146292_n}（buttonList）。 */
    public java.util.List<?> children() {
        return this.field_146292_n;
    }

    // ---- 把 1.12.2 的钩子接到上面那三个 1.14 名字上 --------------------------------

    @Override
    public void func_146280_a(net.minecraft.client.Minecraft mc, int w, int h) {
        // 1.12.2 的 func_146280_a **不调** func_183500_a：它自己设 field_146294_l/m，
        // 然后直接调 func_73866_w_()（initGui → 我们的 init）。所以必须在这里、
        // 在调 super 之前把 1.14 时代的字段先同步好，否则 init() 里读到的是 0x0
        // （踩过：gui=0x0 panel=-6x-6，children 也跟着少一个）。
        this.width = w;
        this.height = h;
        if (mc != null) this.font = new Font(mc.field_71466_p);
        super.func_146280_a(mc, w, h);
    }

    @Override
    public void func_183500_a(int w, int h) {
        this.width = w;
        this.height = h;
        super.func_183500_a(w, h);
    }

    @Override
    public void func_73866_w_() {
        super.func_73866_w_();
        init();
    }

    @Override
    public void func_73876_c() {
        super.func_73876_c();
        tick();
    }
}
