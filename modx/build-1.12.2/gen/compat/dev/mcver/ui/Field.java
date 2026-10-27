package dev.mcver.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;

/**
 * 1.12.2 兼容层：输入框。
 *
 * <p>构造器是 {@code (id, TextRenderer, x, y, w, h)}，提示文字后设；
 * 而且 <b>没有 {@code setChangedListener}</b> —— 1.12 只能 {@code setListener(PagedEntryListWidget.Listener)}，
 * 那是给选项列表用的，不是文本变化回调。所以这里自己注册一张表，每帧比对文本，变了就回调
 * （{@link ChromeScreen} 在 render 里调 {@link #poll()}）。
 */
public final class Field {

    private Field() { }

    private static final Map<TextFieldWidget, Consumer<String>> WATCHERS = new HashMap<>();
    private static final Map<TextFieldWidget, String> LAST = new HashMap<>();
    private static final List<TextFieldWidget> ORDER = new ArrayList<>();

    public static TextFieldWidget of(TextRenderer tr, int x, int y, int w, int h, String placeholder) {
        TextFieldWidget f = new TextFieldWidget(0, tr, x, y, w, h);
        f.setMaxLength(512);
        return f;
    }

    /** 对应 1.14+ 的 {@code setChangedListener}。 */
    public static void changed(TextFieldWidget f, Consumer<String> c) {
        if (!WATCHERS.containsKey(f)) ORDER.add(f);
        WATCHERS.put(f, c);
        LAST.put(f, f.getText());
    }

    /** 每帧一次：发现文本变了就回调。 */
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

    /** 换页时清掉旧控件，避免表越积越大。 */
    static void clear() {
        WATCHERS.clear();
        LAST.clear();
        ORDER.clear();
    }
}
