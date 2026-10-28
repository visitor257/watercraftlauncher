package dev.mcver.bridge;

/**
 * 1.7.10 的 {@code net.minecraft.client.Minecraft} 门面。
 *
 * <p><b>为什么需要它</b>：{@code net.minecraft.client.Minecraft} 在 1.7.10 里本来就有，
 * 同名垫片进 mod jar 会跟原版抢，而 LaunchClassLoader 的 URL 顺序不保证谁赢 —— 那种 bug
 * 只在某些启动路径上出现。所以换包 + 构建时把 import 的全限定名整串替换掉。
 *
 * <p>1.7.10 与 1.12.2 的差别：剪贴板在 {@code GuiScreen}（静态），字体是
 * {@code field_71466_p}，退出是 {@code func_71400_g}，调度是 {@code func_152344_a}。
 */
public final class Minecraft {

    private final net.minecraft.client.Minecraft handle;

    private Minecraft(net.minecraft.client.Minecraft handle) {
        this.handle = handle;
    }

    public static Minecraft getInstance() {
        Minecraft m = new Minecraft(net.minecraft.client.Minecraft.func_71410_x());
        m.gameDirectory = m.handle.field_71412_D;
        m.font = new net.minecraft.client.gui.Font(m.handle.field_71466_p);
        return m;
    }

    /** 1.14+ 的 {@code gameDirectory}。 */
    public java.io.File gameDirectory;

    /** 1.14+ 的 {@code font}。 */
    public net.minecraft.client.gui.Font font;

    /** 1.14+ 的 {@code keyboardHandler}：只实现页面用到的那一个方法。 */
    public final Clipboard keyboardHandler = new Clipboard();

    public void execute(Runnable r) {
        handle.func_152344_a(r);
    }

    public void setScreen(net.minecraft.client.gui.GuiScreen screen) {
        handle.func_147108_a(screen);
    }

    public net.minecraft.client.gui.GuiScreen currentScreen() {
        return handle.field_71462_r;
    }

    /** 1.14+ 的 {@code stop()}（退出游戏）。 */
    public void stop() {
        handle.func_71400_g();
    }

    public net.minecraft.client.Minecraft unwrap() {
        return handle;
    }

    /** 1.14+ 的 {@code KeyboardHandler}，只留剪贴板。 */
    public static final class Clipboard {

        /** 1.14+ 的 {@code getClipboard()}。 */
        public String getClipboard() {
            return net.minecraft.client.gui.GuiScreen.func_146277_j();
        }

        /** 1.14+ 的 {@code setClipboard(String)}。 */
        public void setClipboard(String s) {
            net.minecraft.client.gui.GuiScreen.func_146275_d(s);
        }
    }
}
