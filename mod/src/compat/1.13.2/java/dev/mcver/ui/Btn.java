package dev.mcver.ui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 1.13.2 兼容层：按钮。
 *
 * <p>1.13 是本工程里唯一「按钮既没有回调接口、又要靠子类」的一代：
 * <ul>
 *   <li>没有 {@code PressAction}（1.14 才有），也没有 1.12 的 {@code buttonClicked(ButtonWidget)} 分发；</li>
 *   <li>点击链路是 {@code ButtonWidget#mouseClicked} → {@code method_18377}(命中判定) →
 *       {@code method_18374(double,double)}（= {@code onClick}）。所以这里<b>继承一个私有子类</b>
 *       覆盖 {@code method_18374} 就行，不用 id 表。</li>
 * </ul>
 *
 * <p><b>坑</b>：6 参构造器是 {@code (id, x, y, width, height, message)} —— <b>id 在最前面</b>
 * （和 1.12 一样，和 1.14 的 {@code (x,y,w,h,msg)} 相反）。传错会把宽度当 id、
 * 按钮的 {@code width} 变成 id 值，屏幕上出现一片盖住整块面板的白条。
 * id 用一个递增计数器发，保证同屏不冲突。
 */
public final class Btn {

    private Btn() { }

    /** 自定义回调（对齐 1.14+ 的 {@code ButtonWidget.PressAction} 用法）。 */
    public interface PressAction {
        void onPress(ButtonWidget button);
    }

    /** 1.13 需要给按钮发 id；同屏唯一即可，值本身没意义。 */
    private static int NEXT_ID = 1;

    public static Builder builder(Text text, PressAction onPress) {
        return new Builder(text, onPress);
    }

    /** 改按钮文案：1.13 没有 {@code setMessage(String)}，只有 public 字段 {@code message}。 */
    public static void label(ButtonWidget button, String s) {
        button.message = s == null ? "" : s;
    }

    /** 真正接回调的按钮。 */
    private static final class Impl extends ButtonWidget {

        private final PressAction onPress;

        Impl(int x, int y, int width, int height, int id, String message, PressAction onPress) {
            super(id, x, y, width, height, message);
            this.onPress = onPress;
        }

        /** = {@code onClick(double,double)}：1.13 的点击终点。 */
        @Override
        public void method_18374(double mouseX, double mouseY) {
            if (onPress != null) onPress.onPress(this);
        }
    }

    public static final class Builder {

        private final Text text;
        private final PressAction onPress;
        private int x;
        private int y;
        private int width = 200;
        private int height = 20;

        private Builder(Text text, PressAction onPress) {
            this.text = text;
            this.onPress = onPress;
        }

        public Builder dimensions(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public ButtonWidget build() {
            return new Impl(x, y, width, height, NEXT_ID++, text.getString(), onPress);
        }
    }
}
