package dev.mcver.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 1.18.2 兼容层：按钮。
 *
 * <p>1.18.2 没有 {@code ButtonWidget.builder(...)} fluent API（1.19 才有），
 * 只能直接 {@code new ButtonWidget(x, y, w, h, text, onPress)}。
 * 所以这里的小 Builder 先把 dimensions 记下来，build() 时一次构造出来。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Text text, ButtonWidget.PressAction onPress) {
        return new Builder(text, onPress);
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
            return new ButtonWidget(x, y, width, height, text, onPress);
        }
    }
}
