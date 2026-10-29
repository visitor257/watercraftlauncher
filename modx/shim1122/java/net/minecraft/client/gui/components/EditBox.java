package net.minecraft.client.gui.components;

/**
 * 1.12.2 垫片：{@code GuiTextField} + 1.15+ 的方法名。
 *
 * <p>差异一览（左＝1.15+，右＝1.12.2 SRG）：
 * <ul>
 *   <li>{@code setText} → {@code func_146180_a}</li>
 *   <li>{@code getText} → {@code func_146179_b}</li>
 *   <li>{@code setMaxLength} → {@code func_146203_f}</li>
 *   <li>{@code setChangedListener} → {@code func_175207_a(GuiResponder)}（1.12.2 给的是接口，
 *       得包一层适配器；这里用一个固定 id 的 responder 转发到 {@link Runnable}）</li>
 * </ul>
 */
public class EditBox extends net.minecraft.client.gui.GuiTextField implements AbstractWidget {

    private Runnable changed;

    public EditBox(net.minecraft.client.gui.FontRenderer font, int x, int y, int w, int h) {
        super(0, font, x, y, w, h);
    }

    public void setText(String s) {
        func_146180_a(s == null ? "" : s);
    }

    /** 有些调用点写的是 1.14 之前的 setValue / getValue。 */
    public void setValue(String s) {
        setText(s);
    }

    public String getValue() {
        return getText();
    }

    public String getText() {
        return func_146179_b();
    }

    public void setMaxLength(int n) {
        func_146203_f(n);
    }

    /**
     * 焦点开关。共享页面在「重建控件之后」要把焦点还给新输入框 ——
     * 否则每敲一个字就被重建一次，只有第一个字能进去（见 DownloadPage 的说明）。
     */
    public void setFocused(boolean focused) {
        func_146195_b(focused);
    }

    public boolean isFocused() {
        return func_146206_l();
    }

    /** 值变化回调（1.15+ 的 setChangedListener）。 */
    public void setChangedListener(java.util.function.Consumer<String> listener) {
        this.changed = listener == null ? null : () -> listener.accept(func_146179_b());
        func_175207_a(new Adaptor(this));
    }

    /** 页面里也有写 setResponder(...) 的（那是文本框自己的叫法）。 */
    public void setResponder(java.util.function.Consumer<String> listener) {
        setChangedListener(listener);
    }

    /** 1.12.2 的回调接口要 (int id, String text)，这里把 id 丢掉。 */
    private static final class Adaptor implements net.minecraft.client.gui.GuiPageButtonList.GuiResponder {

        private final EditBox box;

        Adaptor(EditBox box) {
            this.box = box;
        }

        @Override
        public void func_175321_a(int id, boolean value) { }

        @Override
        public void func_175319_a(int id, String value) {
            if (box.changed != null) box.changed.run();
        }

        @Override
        public void func_175320_a(int id, float value) { }
    }
}
