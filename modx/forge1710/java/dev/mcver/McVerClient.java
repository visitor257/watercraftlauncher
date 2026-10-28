package dev.mcver;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端逻辑（MC 1.7.10 / Forge 10.13.x）。
 *
 * <p>事件系统跟 1.12.2 同构（{@code @SubscribeEvent} + {@code MinecraftForge.EVENT_BUS}），
 * 但包在 {@code cpw.mods.fml.common.eventhandler} / {@code cpw.mods.fml.common.gameevent}。
 * 按键同样是 LWJGL2 的 {@code org.lwjgl.input.Keyboard}。
 */
public final class McVerClient {

    private static boolean autoOpened;
    private static Object lastScreen;
    private static boolean mDown;
    private static boolean inited;

    private McVerClient() { }

    public static void install() {
        // 1.7.10 有**两个**事件总线：TickEvent 走 FMLCommonHandler.instance().bus()，
        // 不走 MinecraftForge.EVENT_BUS（1.8+ 才合并成一个）。只听 Forge 总线的话
        // tick 永远收不到 —— 表现是日志里没有「客户端初始化完成」，也没有 cur=。
        McVerClient handler = new McVerClient();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(handler);
        MinecraftForge.EVENT_BUS.register(handler);
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
