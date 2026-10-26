package dev.mcver.ui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * 文本适配层：一行字面量文本。
 *
 * <p>{@code Text.literal(String)} 是 1.19 才加的工厂方法，1.18.2 及更早只有
 * {@code new LiteralText(String)} / {@code Text.of(String)}。UI 里近百处都只是
 * 「给个字符串当控件文字」，没必要每个 target 复制一份页面代码，所以收口到这里。
 *
 * <p>每个 target 一份实现：
 * <ul>
 *   <li>1.19+（本文件）：直接 {@link Text#literal};</li>
 *   <li>1.18.2 及更早（{@code src/compat/<target>/java}）：{@code Text.of}。</li>
 * </ul>
 */
public final class Txt {

    private Txt() { }

    public static MutableText of(String s) {
        return Text.literal(s == null ? "" : s);
    }
}
