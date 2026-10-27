package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.Txt;
import dev.mcver.ui.UiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口（NeoForge 21.1 / MC 1.21.1）：物品右键打开启动器，另外留一个 M 键兜底。
 *
 * <p>逻辑和 Fabric 版一模一样，只换了命名与事件机制：
 * <ul>
 *   <li>Fabric {@code ClientTickEvents.END_CLIENT_TICK} → NeoForge {@code ClientTickEvent.Post}；</li>
 *   <li>{@code client.options.useKey} → {@code client.options.keyUse}；</li>
 *   <li>{@code client.currentScreen} → {@code client.screen}；{@code Hand} → {@code InteractionHand}；</li>
 *   <li>键盘仍然读 GLFW 原始键位（不依赖各版本 KeyMapping 的构造签名，跨版本最省事）。</li>
 * </ul>
 *
 * <p>{@code MCVER_OPEN_GUI} 时进标题界面自动打开主界面（验证脚本用）。
 */
@EventBusSubscriber(modid = McVerMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class McVerClient {

    /** 自检钩子：进标题界面就自动打开，只做一次。 */
    private static boolean autoOpened;
    /** 上一次看到的屏幕类名，变了才打日志（排查「界面没出来」用）。 */
    private static Object lastScreen;
    /** M 键的上一帧状态，用来做「按下的那一瞬」判定。 */
    private static boolean mDown;
    private static boolean inited;

    private McVerClient() { }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (!inited) {
            inited = true;
            Config.load();
            McVerMod.LOGGER.info("mcver: 客户端初始化完成");
        }

        if (System.getenv("MCVER_OPEN_GUI") != null) autoOpenHook(client);

        if (client.player == null) {
            mDown = false;
            return;
        }
        // 界面上开着别的屏（聊天、背包、暂停菜单）时不要抢输入
        if (client.screen != null) {
            mDown = isMDown(client);
            return;
        }

        boolean rightClick = client.options.keyUse.consumeClick() && holdingTool(client);

        boolean down = isMDown(client);
        boolean mPressed = down && !mDown;
        mDown = down;

        if (rightClick || mPressed) {
            McVerMod.LOGGER.info(rightClick ? "mcver: 右键打开启动器" : "mcver: M 键打开启动器");
            client.execute(McVerClient::openMain);
        }
    }

    /** 原始键位：LWJGL 的 GLFW 跟 MC 版本无关。 */
    private static boolean isMDown(Minecraft client) {
        try {
            long win = client.getWindow().getWindow();
            return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 手上（主手或副手）拿着那个物品。 */
    private static boolean holdingTool(Minecraft client) {
        if (client.player == null) return false;
        ItemStack main = client.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main != null && main.getItem() == McVerMod.tool()) return true;
        ItemStack off = client.player.getItemInHand(InteractionHand.OFF_HAND);
        return off != null && off.getItem() == McVerMod.tool();
    }

    /** 自检钩子：进标题界面自动开一次。 */
    private static void autoOpenHook(Minecraft client) {
        if (client.screen != lastScreen) {
            lastScreen = client.screen;
            McVerMod.LOGGER.info("mcver: cur=" + (lastScreen == null ? "null" : lastScreen.getClass().getName()));
        }
        if (autoOpened || !(client.screen instanceof TitleScreen)) return;
        autoOpened = true;
        openMain();
    }

    /** 主界面；{@code MCVER_SCREEN=wizard|download} 时直接进下载页。 */
    static void openMain() {
        Minecraft mc = Minecraft.getInstance();
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
        MainScreen screen = new MainScreen(mc.screen);
        mc.setScreen(screen);
        if ("wizard".equalsIgnoreCase(which) || "download".equalsIgnoreCase(which)) {
            screen.switchTo("下载");
        }
    }

    /** 当前游戏目录，给 UI 当默认值。 */
    public static java.nio.file.Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Txt.of(s));
        }
    }
}
