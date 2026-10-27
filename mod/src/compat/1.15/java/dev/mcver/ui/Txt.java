package dev.mcver.ui;

import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;

/**
 * 1.15 兼容层：文本。
 *
 * <p>1.15 既没有 {@code Text.literal(String)}（1.19 才有），也没有 {@code MutableText}
 * 这个类型（1.16 才拆出来），只有 {@link LiteralText}。页面代码只把它当「一段字」传给
 * 按钮/绘制层，所以这里返回 {@link Text} 就够。
 */
public final class Txt {

    private Txt() { }

    public static Text of(String s) {
        return new LiteralText(s == null ? "" : s);
    }
}
