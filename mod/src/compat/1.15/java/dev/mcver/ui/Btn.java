package dev.mcver.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 1.15 兼容层：按钮。
 *
 * <p>1.15 的 {@code ButtonWidget} 构造器第 5 个参数是 {@code String}
 * （1.16 起才改成 {@code Text}），所以 builder 里要 {@code getString()} 一下。
 * 同样没有 {@code ButtonWidget.builder(...)}（1.19 才有），只能存下尺寸后一次构造。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Text text, ButtonWidget.PressAction onPress) {
        return new Builder(text, onPress);
    }

    /** 改按钮文字：1.15 的 {@code setMessage} 收 {@code String}。 */
    public static void label(ButtonWidget button, String s) {
        button.setMessage(s == null ? "" : s);
    }

    public static final class Builder {

        private final Text text;
        private final ButtonWidget.PressAction onPress;
        private int x;
        private int y;
        private int width = 200;
        private int height = 20;

        private Builder(Text text, ButtonWidget.PressAction onPress) {
            this.text = text;
            this.onPress = onPress;
        }

        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public ButtonWidget build() {
            return new ButtonWidget(x, y, width, height, text.getString(), onPress);
        }
    }
}
