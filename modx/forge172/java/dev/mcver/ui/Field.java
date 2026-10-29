package dev.mcver.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;

/**
 * 输入框适配层（MC 1.7.10）。
 *
 * <p>1.7.10 的 {@code GuiTextField} 构造器是 {@code (FontRenderer, x, y, w, h)} ——
 * 连 id 参数都没有（1.12.2 有，1.8 才加的）。占位符概念更不存在（1.16 才有），直接丢掉。
 */
public final class Field {

    private Field() { }

    public static EditBox of(Font tr, int x, int y, int w, int h, String placeholder) {
        return new EditBox(tr.unwrap(), x, y, w, h);
    }
}
