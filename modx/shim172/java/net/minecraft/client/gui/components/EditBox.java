package net.minecraft.client.gui.components;

/**
 * 1.7.10 垫片：{@code GuiTextField} + 1.15+ 的方法名。
 *
 * <p>跟 1.12.2 那份的三处差别：
 * <ol>
 *   <li>构造器是 {@code (FontRenderer, x, y, w, h)}，<b>没有 id 参数</b>；</li>
 *   <li>1.7.10 <b>没有 {@code GuiResponder}</b>（那是 1.8 才加的），所以「值变化回调」
 *       只能轮询：{@link #poll()} 比较缓存的字符串，变了就 fire。
 *       {@code ChromeScreen} 每帧画之前调一次；</li>
 *   <li>鼠标点击要自己转发（{@code GuiScreen} 不管这些框）。</li>
 * </ol>
 */
public class EditBox extends net.minecraft.client.gui.GuiTextField implements AbstractWidget {

    private java.util.function.Consumer<String> listener;
    private String last;

    public EditBox(net.minecraft.client.gui.FontRenderer font, int x, int y, int w, int h) {
        super(font, x, y, w, h);
        this.last = func_146179_b();
    }

    public void setText(String s) {
        func_146180_a(s == null ? "" : s);
        this.last = func_146179_b(); // 程序设值不算用户改动，不 fire
    }

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

    /** 1.15+ 的 {@code setChangedListener}。1.7.10 只能靠 {@link #poll()}。 */
    public void setChangedListener(java.util.function.Consumer<String> listener) {
        this.listener = listener;
    }

    /** 页面里也有写 {@code setResponder(...)} 的。 */
    public void setResponder(java.util.function.Consumer<String> listener) {
        setChangedListener(listener);
    }

    /** 每帧调：文本变了就通知一次。 */
    public void poll() {
        String now = func_146179_b();
        if (this.last == null ? now != null : !this.last.equals(now)) {
            this.last = now;
            if (this.listener != null) this.listener.accept(now);
        }
    }
}
