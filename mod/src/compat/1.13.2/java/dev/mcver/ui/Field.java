package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;

import java.util.function.Consumer;

/**
 * 1.13.2 兼容层：输入框。
 *
 * <p>构造器是 {@code (id, TextRenderer, x, y, w, h)} —— 第一个参数是 <b>id</b>（1.13 还没有
 * {@code Text} 版提示文字，也没有 1.16 那种 7 参重载）。占位提示这一代没有，只能忽略。
 *
 * <p>文本变化回调名叫 {@code method_18387(BiConsumer<Integer, String>)}（yarn 没命名），
 * 收的是「行号 + 文本」，所以这里包一层，把 {@link Consumer Consumer&lt;String&gt;} 交出去 ——
 * 页面代码里写的是 1.16+ 的 {@code f.setChangedListener(s -> ...)}，构建脚本会机械改成本类的
 * {@link #changed(TextFieldWidget, Consumer)}。
 */
public final class Field {

    private Field() { }

    private static int NEXT_ID = 1;

    public static TextFieldWidget of(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(NEXT_ID++, tr, x, y, w, h);
        f.setMaxLength(512);
        return f;
    }

    /** 对应 1.16+ 的 {@code setChangedListener(Consumer<String>)}。 */
    public static void changed(TextFieldWidget f, Consumer<String> c) {
        if (f == null) return;
        f.method_18387((line, text) -> c.accept(text));
    }
}
