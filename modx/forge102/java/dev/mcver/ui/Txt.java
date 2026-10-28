package dev.mcver.ui;

import net.minecraft.network.chat.Component;

/**
 * 文本适配层（MC 1.12.2）。
 *
 * <p>页面代码写的是 1.19+ 的 {@code Component.literal(...)}；这里转到
 * {@link Component#literal}（内部包 {@code TextComponent}）。
 */
public final class Txt {

    private Txt() { }

    public static Component of(String s) {
        return Component.literal(s == null ? "" : s);
    }
}
