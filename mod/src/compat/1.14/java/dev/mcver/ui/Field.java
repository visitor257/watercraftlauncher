package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;

/** 1.15 兼容层：输入框的提示文字收 {@code String}（1.16 起才收 {@code Text}）。 */
public final class Field {

    private Field() { }

    public static TextFieldWidget of(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        return new TextFieldWidget(tr, x, y, w, h, placeholder == null ? "" : placeholder);
    }
}
