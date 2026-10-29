package dev.mcver.ui;

import net.minecraft.client.gui.Font;

/** 量字宽适配层（MC 1.12.2）：1.12.2 只有 {@code FontRenderer#func_78256_a}，由 {@link Font} 垫片包好。 */
public final class Measure {

    private Measure() { }

    public static int width(Font tr, String s) {
        return tr.width(s == null ? "" : s);
    }
}
