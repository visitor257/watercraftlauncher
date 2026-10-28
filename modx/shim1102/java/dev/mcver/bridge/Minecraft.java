package dev.mcver.bridge;

/**
 * 1.12.2 的 {@code Minecraft} 门面。
 *
 * <p><b>为什么不能像别的垫片那样直接叫 {@code net.minecraft.client.Minecraft}</b>：
 * 这个类名在 1.12.2 里<b>已经存在</b>（原版就有），往 mod jar 里再放一份会跟原版撞车 ——
 * 而 LaunchClassLoader 的加载顺序是 URL 顺序，谁赢没有保证。那属于「今天能跑、明天崩」的坑。
 *
 * <p>所以这里换到 {@code dev.mcver.bridge} 包，由构建脚本对生成源码做一次文本替换
 * （{@code net.minecraft.client.Minecraft} → {@code dev.mcver.bridge.Minecraft}），
 * 页面代码里的 {@code Minecraft xxx = Minecraft.getInstance();} 因此完全不用改。
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

    /** 1.14+ 的 {@code gameDirectory}；1.12.2 是 public field {@code field_71412_D}。 */
    public java.io.File gameDirectory;

    /** 1.14+ 的 {@code font}；1.12.2 是 public field {@code field_71466_p}（FontRenderer）。 */
    public net.minecraft.client.gui.Font font;

    /** 1.14+ 的 {@code keyboardHandler}：这里只实现页面用到的那一个方法。 */
    public final Clipboard keyboardHandler = new Clipboard();

    /** 1.14+ 的 {@code execute}；1.12.2 是 {@code func_152344_a}（addScheduledTask）。 */
    public void execute(Runnable r) {
        handle.func_152344_a(r);
    }

    /** 1.14+ 的 {@code setScreen}；1.12.2 是 {@code func_147108_a}（displayGuiScreen）。 */
    public void setScreen(net.minecraft.client.gui.GuiScreen screen) {
        handle.func_147108_a(screen);
    }

    /** 当前界面：1.14+ 是 {@code currentScreen}；1.12.2 是 public field {@code field_71462_r}。 */
    public net.minecraft.client.gui.GuiScreen currentScreen() {
        return handle.field_71462_r;
    }

    public net.minecraft.client.Minecraft unwrap() {
        return handle;
    }

    /** 1.14+ 的 {@code stop()}（退出游戏）；1.12.2 是 {@code func_71400_g}。 */
    public void stop() {
        handle.func_71400_g();
    }

    /** 剪贴板。1.12.2 的 Minecraft 上没有 keyboardHandler（那是 1.13+ 的东西）。 */
    public static final class Clipboard {

        public void setClipboard(String s) {
            net.minecraft.client.gui.GuiScreen.func_146275_d(s);
        }

        public String getClipboard() {
            return net.minecraft.client.gui.GuiScreen.func_146277_j();
        }
    }
}
