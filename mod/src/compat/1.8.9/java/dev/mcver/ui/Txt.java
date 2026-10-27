package dev.mcver.ui;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

/** 1.12.2 兼容层：文本（无 {@code Text.literal}，只有 {@link LiteralText}）。 */
public final class Txt {

    private Txt() { }

    public static Text of(String s) {
        return new LiteralText(s == null ? "" : s);
    }
}
