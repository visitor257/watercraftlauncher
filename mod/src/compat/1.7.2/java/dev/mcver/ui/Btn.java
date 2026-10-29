package dev.mcver.ui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 1.12.2 兼容层：按钮。
 *
 * <p>1.12 的 {@code ButtonWidget} <b>没有</b> {@code PressAction} 之类的回调接口 ——
 * 点击是由 {@code Screen#buttonClicked(ButtonWidget)} 按 {@code id} 分发的。
 * 所以这里：给每个按钮分配唯一 id，把回调存进静态表，{@link ChromeScreen} 覆写
 * {@code buttonClicked} 去查表。
 *
 * <p>另外构造器是 {@code (id,x,y,width,height,String)}（<b>id 在最前面</b>，文案是 String），
 * 文字存在 public 字段 {@code message} 里。
 */
public final class Btn {

    private Btn() { }

    /** 我们自己定义的回调（1.12 没有官方版本）。 */
    public interface PressAction {
        void onPress(ButtonWidget button);
    }

    private static final Map<Integer, PressAction> ACTIONS = new HashMap<>();
    private static int nextId = 3000;

    public static Builder builder(Text text, PressAction onPress) {
        return new Builder(text, onPress);
    }

    /** 改按钮文字：1.7.2 的 yarn 没给 {@code message} 起名，字段是 {@code field_22510}。 */
    public static void label(ButtonWidget button, String s) {
        button.field_22510 = s == null ? "" : s;
    }

    /** 给 {@link ChromeScreen} 用：按下时执行对应回调；返回是否命中。 */
    static boolean dispatch(ButtonWidget button) {
        PressAction a = ACTIONS.get(button.id);
        if (a == null) return false;
        a.onPress(button);
        return true;
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
            int id = nextId++;
            ButtonWidget b = new ButtonWidget(id, x, y, width, height, text.asFormattedString());
            ACTIONS.put(id, onPress);
            return b;
        }
    }
}
