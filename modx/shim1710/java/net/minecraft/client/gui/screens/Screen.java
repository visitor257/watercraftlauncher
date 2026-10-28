package net.minecraft.client.gui.screens;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 1.7.10 垫片：{@code GuiScreen} + 1.14 时代字段名。
 *
 * <p>跟 1.12.2 那份（{@code shim1122}）几乎一样，只有一处必须改：
 * <b>1.7.10 没有 {@code func_183500_a}</b>（那个方法是 1.9 才有的），所以这里不能再
 * override 它。同步 {@code width/height/font} 只能靠覆盖 {@code func_146280_a}
 * 并在 super 之前赋值 —— 因为 {@code func_146280_a} 里直接调 {@code func_73866_w_}（init）。
 *
 * <p>{@code net.minecraft.client.gui.screens} 这个包在 1.7.10 里不存在，所以不会撞名。
 */
public class Screen extends net.minecraft.client.gui.GuiScreen {

    /** 1.14+ 的 {@code Screen#font}。 */
    protected Font font;

    /** 1.14+ 的 {@code Screen#width} / {@code height}。 */
    public int width;
    public int height;

    protected Screen(Component title) {
        // 1.7.10 的 GuiScreen 没有标题概念
    }

    /** 1.14+ 的 {@code init()}。 */
    protected void init() { }

    /** 1.14+ 的 {@code tick()}。 */
    public void tick() { }

    /** 1.14+ 的 {@code shouldCloseOnEsc()}。 */
    protected boolean shouldCloseOnEsc() {
        return true;
    }

    /** 1.14+ 的 {@code Screen#children()}。 */
    public java.util.List<?> children() {
        return this.field_146292_n;
    }

    // ---- 把 1.7.10 的钩子接到上面那三个 1.14 名字上 --------------------------------

    @Override
    public void func_146280_a(net.minecraft.client.Minecraft mc, int w, int h) {
        // 顺序要紧：1.7.10 的 func_146280_a 自己设 field_146294_l/m，然后直接
        // 调 func_73866_w_()（initGui → 我们的 init）。所以 width/height/font 必须
        // 在 super 之前同步好，否则 init() 里读到 0x0（1.12.2 那轮踩过同一个坑）。
        this.width = w;
        this.height = h;
        if (mc != null) this.font = new Font(mc.field_71466_p);
        super.func_146280_a(mc, w, h);
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
