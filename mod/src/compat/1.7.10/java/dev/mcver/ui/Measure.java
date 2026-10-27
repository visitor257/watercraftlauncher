package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;

/** 1.12.2 兼容层：量字宽（只有 {@code getStringWidth}）。 */
public final class Measure {

    private Measure() { }

    public static int width(TextRenderer tr, String s) {
        return tr.getStringWidth(s == null ? "" : s);
    }
}
