package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;

/**
 * 量字宽适配层。
 *
 * <p>{@code TextRenderer#getWidth(String)} 是 1.16 才加的；1.15 及更早只有
 * {@code getStringWidth(String)}。页面里 4 处居中/右对齐要量宽度，统一走这里。
 */
public final class Measure {

    private Measure() { }

    public static int width(TextRenderer tr, String s) {
        return tr.getWidth(s == null ? "" : s);
    }
}
