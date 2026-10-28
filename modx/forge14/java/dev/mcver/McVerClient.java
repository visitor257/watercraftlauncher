package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.Txt;
import dev.mcver.ui.UiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端逻辑（Forge 28.x / MC 1.14.4）：物品右键打开启动器，M 键兜底。
 *
 * <p>和 Fabric / NeoForge 版同一套逻辑，只换事件入口：
 * {@code TickEvent.ClientTickEvent} 每 tick 触发<b>两次</b>（START / END），要按 {@code phase} 过滤，
 * 否则右键和 M 键判定都会跑两遍。
 */
public final class McVerClient {

    private static boolean autoOpened;
    private static Object lastScreen;
    private static boolean mDown;
    private static boolean inited;

    private McVerClient() { }

    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

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

    private static boolean isMDown(Minecraft client) {
        try {
            long win = client.window.getWindow();
            return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean holdingTool(Minecraft client) {
        if (client.player == null) return false;
        ItemStack main = client.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main != null && main.getItem() == McVerMod.tool()) return true;
        ItemStack off = client.player.getItemInHand(InteractionHand.OFF_HAND);
        return off != null && off.getItem() == McVerMod.tool();
    }

    private static void autoOpenHook(Minecraft client) {
        if (client.screen != lastScreen) {
            lastScreen = client.screen;
            McVerMod.LOGGER.info("mcver: cur=" + (lastScreen == null ? "null" : lastScreen.getClass().getName()));
        }
        if (autoOpened || !(client.screen instanceof TitleScreen)) return;
        autoOpened = true;
        openMain();
    }

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

    static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(Txt.of(s));
        }
    }
}
