package dev.mcver.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 按钮适配层（MC 1.12.2）。
 *
 * <p>1.12.2 既没有 {@code Button.builder(...)}，也没有 {@code Button.OnPress} ——
 * 只有 {@code GuiButton} 子类 + 覆盖 {@code func_146116_c}。所以这里提供 1.15+ 的
 * builder 形状（{@code dimensions(...).build()}），页面代码一个字都不用改。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Component text, Button.OnPress onPress) {
        return new Builder(text, onPress);
    }

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
            String label = this.text == null ? "" : this.text.getString();
            return new Button(x, y, width, height, label, this.onPress);
        }
    }

    /** 改按钮文案。 */
    public static void label(Button button, String text) {
        button.setMessage(text);
    }
}
