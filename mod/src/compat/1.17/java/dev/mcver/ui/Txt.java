package dev.mcver.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * 1.17.1 兼容层：文本。
 *
 * <p>1.17.1 还没有 {@code Text.literal(String)}（1.19 才加），只有 {@code Text.of(String)}
 * 和 {@code new LiteralText(String)}。这里用 {@code Text.of}，它返回的正是 {@code MutableText}。
 */
public final class Txt {

    private Txt() { }

    public static MutableText of(String s) {
        return (MutableText) Text.of(s == null ? "" : s);
    }
}
