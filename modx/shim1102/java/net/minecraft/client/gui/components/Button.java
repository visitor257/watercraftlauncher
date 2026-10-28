package net.minecraft.client.gui.components;

/**
 * 1.12.2 垫片：{@code GuiButton} + 回调式构造器。
 *
 * <p>1.15+ 的 {@code Button} 是 {@code new Button(x, y, w, h, Component, OnPress)}；
 * 1.12.2 只能继承 {@code GuiButton} 并覆盖点击钩子。1.12.2 的点击入口是
 * {@code func_146116_c(Minecraft, int, int)}（mousePressed），返回 true 表示这一下被吃掉了。
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
            this.func_146113_a(mc.func_147118_V()); // 播放点击音效（1.12.2 的按钮默认会做）
            this.press.onPress(this);
        }
        return hit;
    }

    /** 1.15+ 叫 {@code setMessage(String)}；1.12.2 是公有字段 {@code field_146126_j}。 */
    public void setMessage(String s) {
        this.field_146126_j = s == null ? "" : s;
    }

    public String getMessage() {
        return this.field_146126_j;
    }

    public void setWidth(int w) {
        this.func_175211_a(w); // GuiButton#setWidth
    }
}
