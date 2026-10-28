package dev.mcver.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 按钮适配层（mojmap 版，MC 1.14.4 / Forge 28.x）。
 *
 * <p>1.14.4 的 {@code Button} 构造器收的是 <b>String</b>（不是 Component）：
 * {@code new Button(x, y, w, h, String, Button.OnPress)}。页面代码统一写
 * {@code Btn.builder(...).dimensions(...).build()}，这里负责 {@code getString()} 转换。
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
            return new Button(x, y, width, height, text == null ? "" : text.getString(), onPress);
        }
    }

    /** 改按钮文案（1.14.4 也是 String）。 */
    public static void label(Button button, String text) {
        button.setMessage(text == null ? "" : text);
    }
}
