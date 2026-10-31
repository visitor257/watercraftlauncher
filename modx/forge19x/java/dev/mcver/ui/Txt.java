package dev.mcver.ui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 文本适配层：1.19 ~ 1.19.2 专用。
 *
 * <p>这三代是「1.18.2 的注册表 API + 1.19 的聊天组件 API」的混合体：
 * {@code new TextComponent(...)}（forge18/Txt 用的写法）在 1.19 被删了，
 * 只留下 {@code Component.literal(...)}；但 {@code Registries.ITEM} / {@code CreativeModeTabEvent}
 * 这些 1.19.3 才有的东西还没有。所以 base 用 forge18，只把 Txt 换成这一份。
 *
 * <p>2026-10-31 实测：1.19 用 forge19 的 overlay 编不过（找不到 BuiltInRegistries / CreativeModeTabEvent），
 * 用 forge18 + 本文件才过。
 */
public final class Txt {

    private Txt() { }

    public static MutableComponent of(String s) {
        return Component.literal(s == null ? "" : s);
    }
}
