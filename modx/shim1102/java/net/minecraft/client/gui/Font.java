package net.minecraft.client.gui;

/**
 * 1.12.2 垫片：1.13+ 的 {@code Font}（原名 {@code FontRenderer}）。
 *
 * <p>页面代码只用到三件事：量宽 {@code width}、截字 {@code plainSubstrByWidth}，
 * 以及把它当不透明句柄传给 {@code Ctx#drawText}。所以这里包一层就够了。
 */
public class Font {

    private final FontRenderer handle;

    public Font(FontRenderer handle) {
        this.handle = handle;
    }

    public FontRenderer unwrap() {
        return handle;
    }

    /** 1.16+ 叫 {@code width}；1.12.2 是 {@code func_78256_a}（getStringWidth）。 */
    public int width(String s) {
        return handle.func_78256_a(s == null ? "" : s);
    }

    /** 1.16+ 叫 {@code plainSubstrByWidth}；1.12.2 是 {@code func_78262_a(s, width, false)}。 */
    public String plainSubstrByWidth(String s, int width) {
        return handle.func_78262_a(s == null ? "" : s, width, false);
    }
}
