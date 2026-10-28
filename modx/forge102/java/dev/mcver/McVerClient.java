package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 客户端逻辑（MC 1.12.2 / Forge 14.23.x）。
 *
 * <p>事件系统是老的 {@code @SubscribeEvent} + {@code MinecraftForge.EVENT_BUS.register(...)}；
 * {@code TickEvent.ClientTickEvent} 每 tick 触发两次（START / END），必须按 phase 过滤。
 *
 * <p>按键用 LWJGL2 的 {@code org.lwjgl.input.Keyboard}（1.12.2 是 LWJGL 2，没有 GLFW）。
 */
public final class McVerClient {

    private static boolean autoOpened;
    private static Object lastScreen;
    private static boolean mDown;
    private static boolean inited;

    private McVerClient() { }

    public static void install() {
        MinecraftForge.EVENT_BUS.register(new McVerClient());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft client = Minecraft.func_71410_x();
        if (!inited) {
            inited = true;
            try {
                Config.load();
            } catch (Throwable t) {
                McVerMod.LOGGER.warn("mcver: 配置读取失败 {}", t.toString());
            }
            McVerMod.LOGGER.info("mcver: 客户端初始化完成");
        }

        if (System.getenv("MCVER_OPEN_GUI") != null) autoOpenHook(client);

        if (client.field_71439_g == null) {
            mDown = false;
            return;
        }
        if (client.field_71462_r != null) {
            mDown = isMDown();
            return;
        }

        boolean down = isMDown();
        boolean mPressed = down && !mDown;
        mDown = down;
        if (mPressed) {
            McVerMod.LOGGER.info("mcver: M 键打开启动器");
            client.func_152344_a(McVerClient::openMain);
        }
    }

    private static boolean isMDown() {
        try {
            return org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_M);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void autoOpenHook(Minecraft client) {
        if (client.field_71462_r != lastScreen) {
            lastScreen = client.field_71462_r;
            McVerMod.LOGGER.info("mcver: cur=" + (lastScreen == null ? "null" : lastScreen.getClass().getName()));
        }
        if (autoOpened) return;
        if (!(client.field_71462_r instanceof GuiMainMenu)) return;
        autoOpened = true;
        McVerMod.LOGGER.info("mcver: 自检钩子打开启动器");
        openMain();
    }

    static void openMain() {
        Minecraft mc = Minecraft.func_71410_x();
        UiState.dir();
        try {
            if (!Config.lastDir.trim().isEmpty()) {
                java.nio.file.Path p = java.nio.file.Paths.get(Config.lastDir);
                if (java.nio.file.Files.isDirectory(p)) UiState.setDir(p);
            }
        } catch (Throwable ignored) { }
        Config.lastDir = UiState.dir().toString();
        Config.save();

        String which = System.getenv("MCVER_SCREEN");
        MainScreen screen = new MainScreen(null);
        mc.func_147108_a(screen);
        if ("wizard".equalsIgnoreCase(which) || "download".equalsIgnoreCase(which)) {
            screen.switchTo("下载");
        }
    }
}
