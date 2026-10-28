package net.minecraft.client.gui.components;

/**
 * 1.7.10 垫片：{@code GuiButton} + 回调式构造器。
 *
 * <p>跟 1.12.2 那份的差别在 {@code setWidth}：1.7.10 <b>没有</b>
 * {@code func_175211_a}（setWidth 是 1.8 加的），宽度是 protected 字段
 * {@code field_146120_f}，直接在子类里赋值。
 *
 * <p>点击入口是 {@code func_146116_c(Minecraft,int,int)}（mousePressed）。
 */
public class Button extends net.minecraft.client.gui.GuiButton implements AbstractWidget {

    /** 1.15+ 的 {@code Button.OnPress}。 */
    public interface OnPress {
        void onPress(Button button);
    }

    private final OnPress press;

    public Button(int x, int y, int width, int height, String text, OnPress press) {
        super(-1, x, y, width, height, text == null ? "" : text);
        this.press = press;
    }

    @Override
    public boolean func_146116_c(net.minecraft.client.Minecraft mc, int mouseX, int mouseY) {
        boolean hit = super.func_146116_c(mc, mouseX, mouseY);
        if (hit && this.press != null) {
            this.func_146113_a(mc.func_147118_V()); // 点击音效
            this.press.onPress(this);
        }
        return hit;
    }

    /** 1.15+ 的 {@code setMessage(String)}。 */
    public void setMessage(String s) {
        this.field_146126_j = s == null ? "" : s;
    }

    public String getMessage() {
        return this.field_146126_j;
    }

    public void setWidth(int w) {
        this.field_146120_f = w; // GuiButton#width（protected）
    }
}
