package dev.mcver.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

/**
 * 输入框适配层（MC 1.12.2）。
 *
 * <p>{@code GuiTextField} 的构造器是 {@code (id, FontRenderer, x, y, w, h)}，<b>没有占位符</b>
 * 这个概念（1.16 才有），而且它不会自己进 {@code buttonList} —— {@code ChromeScreen}
 * 单独存一份并负责绘制和转键盘。占位符在这儿直接丢掉（纯外观，不影响功能）。
 */
public final class Field {

    private Field() { }

    public static EditBox of(Font tr, int x, int y, int w, int h, String placeholder) {
        return new EditBox(tr.unwrap(), x, y, w, h);
    }
}
