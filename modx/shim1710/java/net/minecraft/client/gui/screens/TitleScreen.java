package net.minecraft.client.gui.screens;

import net.minecraft.network.chat.Component;

/** 1.12.2 垫片：1.14+ 的 {@code TitleScreen}，这里只是 {@link Screen} 的别名，用来当「父屏」占位。 */
public class TitleScreen extends Screen {

    public TitleScreen() {
        super((Component) null);
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float delta) {
        // 不做任何事：这个垫片只当类型/占位用，不会真的被显示
    }
}
