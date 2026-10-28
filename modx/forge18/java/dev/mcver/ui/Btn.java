package dev.mcver.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 按钮适配层（mojmap 版，MC 1.18.2）。
 *
 * <p>{@code Button.builder(...)} 是 1.19 才加的，1.18.2 只有构造器：
 * {@code new Button(x, y, w, h, Component, Button.OnPress)}。
 * 页面代码统一写 {@code Btn.builder(...).dimensions(...).build()}，这个类负责翻译。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Component text, Button.OnPress onPress) {
        return new Builder(text, onPress);
    }

    /** 只包住我们实际用到的那两个 builder 方法：bounds / build。 */
    public static final class Builder {

        private final Component text;
        private final Button.OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component text, Button.OnPress onPress) {
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

        public Button build() {
            return new Button(x, y, width, height, text, onPress);
        }
    }

    /** 改按钮文案（1.18.2 没有 Component.literal，走 Txt）。 */
    public static void label(Button button, String text) {
        button.setMessage(Txt.of(text));
    }
}
