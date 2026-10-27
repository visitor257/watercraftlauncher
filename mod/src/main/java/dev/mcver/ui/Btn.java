package dev.mcver.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 按钮适配层。
 *
 * <p>{@code ButtonWidget.builder(text, onPress).dimensions(x, y, w, h).build()} 这套 fluent API
 * 是 1.19 才有的；1.18.2 及更早只能 {@code new ButtonWidget(x, y, w, h, text, onPress)}。
 * UI 里 70 多处按钮都写成 builder 链，为了不把页面代码按版本复制一遍，统一走这里。
 *
 * <p>用法照旧写 {@code Btn.builder(text, b -> ...).dimensions(...).build()}。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Text text, ButtonWidget.PressAction onPress) {
        return new Builder(text, onPress);
    }

    /** 改按鈕文字：1.16+ 收 {@code Text}，1.15 及更早收 {@code String}。 */
    public static void label(ButtonWidget button, String s) {
        button.setMessage(Txt.of(s));
    }

    /** 只包住我们实际用到的那几个 builder 方法：dimensions / build。 */
    public static final class Builder {

        private final ButtonWidget.Builder inner;

        private Builder(Text text, ButtonWidget.PressAction onPress) {
            this.inner = ButtonWidget.builder(text, onPress);
        }

        public Builder dimensions(int x, int y, int width, int height) {
            inner.dimensions(x, y, width, height);
            return this;
        }

        public ButtonWidget build() {
            return inner.build();
        }
    }
}
