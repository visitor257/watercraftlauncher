package dev.mcver.ui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * 按钮适配层（mojmap 版）。
 *
 * <p>yarn 的 {@code ButtonWidget.builder(text, onPress).dimensions(x,y,w,h).build()}
 * 在 mojmap 里是 {@code Button.builder(component, onPress).bounds(x,y,w,h).build()}。
 * 页面代码统一写这里，换 loader / 换版本只改这一个文件。
 */
public final class Btn {

    private Btn() { }

    public static Builder builder(Component text, Button.OnPress onPress) {
        return new Builder(text, onPress);
    }

    /** 只包住我们实际用到的那两个 builder 方法：bounds / build。 */
    public static final class Builder {

        private final Button.Builder inner;

        private Builder(Component text, Button.OnPress onPress) {
            this.inner = Button.builder(text, onPress);
        }

        public Builder dimensions(int x, int y, int width, int height) {
            inner.bounds(x, y, width, height);
            return this;
        }

        public Button build() {
            return inner.build();
        }
    }
}
