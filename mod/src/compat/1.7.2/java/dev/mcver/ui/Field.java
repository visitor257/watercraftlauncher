package dev.mcver.ui;

import java.util.function.Consumer;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;

/**
 * 1.7.10 兼容层：输入框。
 *
 * <p>构造器是 {@code (TextRenderer, x, y, w, h)} —— 最老那一套，<b>没有 id 参数</b>
 * （1.8 起才有），也没有 {@code setChangedListener}。
 * 文本变化靠 {@link ChromeScreen} 每帧 {@link #poll()} 比对（同 1.8~1.12 的做法）。
 */
public final class Field {

    private Field() { }

    private static final java.util.Map<TextFieldWidget, Consumer<String>> WATCHERS = new java.util.HashMap<>();
    private static final java.util.Map<TextFieldWidget, String> LAST = new java.util.HashMap<>();
    private static final java.util.List<TextFieldWidget> ORDER = new java.util.ArrayList<>();

    public static TextFieldWidget of(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(tr, x, y, w, h);
        f.setMaxLength(512);
        return f;
    }

    /** 对应 1.16+ 的 {@code setChangedListener(Consumer<String>)}。 */
    public static void changed(TextFieldWidget f, Consumer<String> c) {
        if (f == null) return;
        if (!WATCHERS.containsKey(f)) ORDER.add(f);
        WATCHERS.put(f, c);
        LAST.put(f, f.getText());
    }

    static void poll() {
        for (int i = 0; i < ORDER.size(); i++) {
            TextFieldWidget f = ORDER.get(i);
            Consumer<String> c = WATCHERS.get(f);
            if (c == null) continue;
            String now = f.getText();
            String before = LAST.get(f);
            if (before == null || !before.equals(now)) {
                LAST.put(f, now);
                c.accept(now);
            }
        }
    }

    static void clear() {
        WATCHERS.clear();
        LAST.clear();
        ORDER.clear();
    }
}
