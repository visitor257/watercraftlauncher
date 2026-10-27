package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;

/**
 * 输入框适配层：把 {@link TextFieldWidget} 的「提示文字」参数统一收成 {@code String}。
 *
 * <p>1.16+ 的构造器第 6 个参数是 {@code Text}，1.15 及更早是 {@code String}。页面代码里
 * 十几处 {@code new TextFieldWidget(..., Txt.of("提示"))} 为此只写 {@code Field.of(..., "提示")}，
 * 每个 target 在自己的 compat 层里决定怎么转 —— 免得每个版本复制一遍页面。
 */
public final class Field {

    private Field() { }

    public static TextFieldWidget of(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        return new TextFieldWidget(tr, x, y, w, h, Txt.of(placeholder));
    }
}
