package net.minecraft.client.gui.components;

/**
 * 1.12.2 垫片：控件的最小公共标记。
 *
 * <p>1.12.2 没有统一的 widget 基类：{@code GuiButton} 和 {@code GuiTextField} 各自
 * 继承 {@code Gui}，且按钮要进 {@code GuiScreen.field_146292_n}、输入框要自己画。
 * 所以这里只能做成<b>接口</b>，由 {@link Button} / {@link EditBox} 各自实现。
 */
public interface AbstractWidget {
}
