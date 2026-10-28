package dev.mcver.ui;

import net.minecraft.network.chat.TextComponent;

/**
 * 文本适配层（mojmap 版，MC 1.14.4 / Forge 28.x）。
 *
 * <p>两个名字差异：
 * <ul>
 *   <li>{@code Component.literal(...)} 是 1.19 才有的静态工厂 → 1.14.4 用
 *       {@code new TextComponent(String)}；</li>
 *   <li>1.14.4 还没有 {@code MutableComponent} 这个接口（1.15 才拆出来）→ 返回类型
 *       直接用具体的 {@code TextComponent}。主源码从不声明 {@code MutableComponent}，
 *       只把结果当 {@code Component} 传下去，所以这样是安全的。</li>
 * </ul>
 */
public final class Txt {

    private Txt() { }

    public static TextComponent of(String s) {
        return new TextComponent(s == null ? "" : s);
    }
}
