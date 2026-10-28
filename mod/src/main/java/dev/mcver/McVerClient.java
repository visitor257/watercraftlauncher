package dev.mcver;

import dev.mcver.ui.Txt;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：物品右键打开启动器，外加一个保底快捷键（M）。
 *
 * <p><b>为什么这里全部用「每 tick 轮询」而不是事件/快捷键注册：</b>
 * 本类要编成一个 jar 通吃 1.21 ~ 1.21.11。这条链上有两处 API 在系列中间断掉，
 * 而且都是硬断（运行期 NoSuchMethodError / 编译期方法不存在）：
 * <ul>
 *   <li>{@code UseItemCallback} 的 Lambda 返回类型：1.21 / 1.21.1 是
 *       {@code TypedActionResult&lt;ItemStack&gt;}，1.21.2 起改成 {@code ActionResult}。
 *       编成一个类就无法同时满足（强转编译不过，不转运行期会 ClassCastException）；</li>
 *   <li>{@code new KeyBinding(...)} 第 4 个参数：1.21.9 起从 {@code String} 改成
 *       {@code KeyBinding.Category} record。用旧签名会在注册那一行抛 NoSuchMethodError。</li>
 * </ul>
 * 换成轮询后，用到的东西在整个系列里都没变：{@code options.useKey}（GameOptions 字段，
 * javap 核对过 1.21 / 1.21.1 / 1.21.4 / 1.21.11 都在）、{@code KeyBinding#wasPressed}、
 * LWJGL 的 {@code GLFW.glfwGetKey}（不是 MC 的 API，版本无关）。
 *
 * <p>代价：M 键不能用「控制」里的按键绑定改（GLFW 直接读原始键位），
 * 而且右键是每 tick 查一次而不是走原版物品使用事件——行为一样，只是判定时机不同。
 *
 * <p>{@code MCVER_OPEN_GUI} 时进标题界面自动打开主界面（验证脚本用）。
 */
public class McVerClient implements ClientModInitializer {

    /** 自检钩子：进标题界面就自动打开，只做一次。 */
    private static boolean autoOpened;
    /** 上一次看到的屏幕类名，变了才打日志（排查「界面没出来」用）。 */
    private static Object lastScreen;
    /** M 键的上一帧状态，用来做「按下的那一瞬」判定。 */
    private static boolean mDown;
    private static boolean usePrev;

    @Override
    public void onInitializeClient() {
        Config.load();
        ClientTickEvents.END_CLIENT_TICK.register(McVerClient::onTick);
        if (System.getenv("MCVER_OPEN_GUI") != null) {
            ClientTickEvents.END_CLIENT_TICK.register(McVerClient::autoOpenHook);
        }
        McVerMod.LOGGER.info("mcver: 客户端初始化完成");
    }

    /** 每 tick：拿着「版本下载器」按右键，或者按 M，就开界面。 */
    private static void onTick(MinecraftClient client) {
        if (client.player == null) {
            mDown = false;
            usePrev = false;
            return;
        }
        // 界面上开着别的屏（聊天、背包、暂停菜单）时不要抢输入
        if (client.currentScreen != null) {
            mDown = isMDown(client);
            usePrev = client.options.useKey.isPressed();
            return;
        }

        // ---- 右键判定：不能只看 wasPressed()/consumeClick()
        // 原版 Minecraft.handleInputEvents() 每 tick 都用
        //     while (keyUse.wasPressed()) doItemUse();
        // 把按下计数（timesPressed）全部消费掉；而我们的 tick 回调在它之后，
        // 那行 wasPressed() 因此永远是 false —— 表现就是「拿着物品右键没反应」。
        // （javap 1.21.4 已确认：wasPressed() 会 timesPressed--，handleInputEvents 里
        //   useKey.wasPressed() → doItemUse() 就在同一个 tick 内跑完。）
        // 改成对「按住」状态做边沿检测：KeyBinding.isPressed() 读的是 pressed 字段，
        // 原版只 setPressed 不清它，所以 tick 末尾照样能看见。
        // 保留 wasPressed() 只作保险：万一某个加载器的 tick 事件早于 handleInputEvents，
        // 那一次也能命中（两种顺序都不会漏）。
        boolean clicked = client.options.useKey.wasPressed();
        boolean useDown = client.options.useKey.isPressed();
        boolean edge = useDown && !usePrev;
        usePrev = useDown;
        boolean rightClick = (clicked || edge) && holdingTool(client);

        boolean down = isMDown(client);
        boolean mPressed = down && !mDown;
        mDown = down;

        if (rightClick || mPressed) {
            McVerMod.LOGGER.info(rightClick ? "mcver: 右键打开启动器" : "mcver: M 键打开启动器");
            client.execute(McVerClient::openMain);
        }
    }

    /** 原始键位：LWJGL 的 GLFW 跟 MC 版本无关，比 InputUtil 稳（1.21.11 把它的签名也改了）。 */
    private static boolean isMDown(MinecraftClient client) {
        try {
            long win = client.getWindow().getHandle();
            return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 手上（主手或副手）拿着那个物品。用 getItem() 比较，不用 isOf（1.16.5 没有 isOf）。 */
    private static boolean holdingTool(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack main = client.player.getStackInHand(Hand.MAIN_HAND);
        if (main != null && main.getItem() == McVerMod.DOWNLOAD_TOOL) return true;
        ItemStack off = client.player.getStackInHand(Hand.OFF_HAND);
        return off != null && off.getItem() == McVerMod.DOWNLOAD_TOOL;
    }

    /** 自检钩子：进标题界面自动开一次。 */
    private static void autoOpenHook(MinecraftClient client) {
        if (client.currentScreen != lastScreen) {
            lastScreen = client.currentScreen;
            McVerMod.LOGGER.info("mcver: cur=" + (lastScreen == null ? "null" : lastScreen.getClass().getName()));
        }
        if (autoOpened || !(client.currentScreen instanceof TitleScreen)) return;
        autoOpened = true;
        openMain();
    }

    /** 主界面；MCVER_SCREEN=wizard|download 时直接进下载页。 */
    static void openMain() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UiState.dir();
        if (!Config.lastDir.trim().isEmpty()) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(Config.lastDir);
                if (java.nio.file.Files.isDirectory(p)) UiState.setDir(p);
            } catch (Exception ignored) { }
        }
        Config.lastDir = UiState.dir().toString();
        Config.save();

        String which = System.getenv("MCVER_SCREEN");
        MainScreen screen = new MainScreen(mc.currentScreen);
        mc.setScreen(screen);
        if ("wizard".equalsIgnoreCase(which) || "download".equalsIgnoreCase(which)) {
            screen.switchTo("下载");
        }
    }

    /** 当前游戏目录，给 UI 当默认值。 */
    public static java.nio.file.Path gameDir() {
        return MinecraftClient.getInstance().runDirectory.toPath();
    }

    static void chat(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Txt.of(s));
        }
    }
}
