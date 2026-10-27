package dev.mcver.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** 文本适配层（mojmap 版）：{@code Text.literal} 在 mojmap 里叫 {@code Component.literal}。 */
public final class Txt {

    private Txt() { }

    public static MutableComponent of(String s) {
        return Component.literal(s == null ? "" : s);
    }
}
