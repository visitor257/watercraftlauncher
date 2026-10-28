package dev.mcver.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;

/**
 * 文本适配层（mojmap 版，MC 1.18.2）。
 *
 * <p>{@code Component.literal(...)} 是 1.19 才加的静态工厂，1.18.2 要自己 new
 * {@link TextComponent}（{@code Component.nullToEmpty} 返回的是 Component，不是 MutableComponent）。
 */
public final class Txt {

    private Txt() { }

    public static MutableComponent of(String s) {
        return new TextComponent(s == null ? "" : s);
    }
}
