package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import dev.mcver.ui.DownloadPage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

/**
 * 1.20.1 兼容层：客户端入口。
 *
 * <p>与主源码（1.21.4）的差异只有一处：`UseItemCallback` 的返回值在 1.20.1 还是
 * `TypedActionResult<ItemStack>`（1.21.2 才简化成 `ActionResult`）。
 */
public class McVerClient implements ClientModInitializer {

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        // ---- 手持「版本下载器」右键
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(McVerMod.DOWNLOAD_TOOL)) {
                if (world.isClient()) {
                    McVerMod.LOGGER.info("mcver: 右键打开启动器");
                    MinecraftClient.getInstance().execute(McVerClient::openMain);
                }
                return world.isClient() ? TypedActionResult.success(stack) : TypedActionResult.pass(stack);
            }
            return TypedActionResult.pass(stack);
        });

        // ---- 快捷键兜底（默认 M，可在控制里改）
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.mcver.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.mcver"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player == null) break;
                openMain();
            }
        });

        // ---- 自检钩子：进标题界面就自动打开
        if (System.getenv("MCVER_OPEN_GUI") != null) {
            ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
                private boolean done;
                @Override
                public void onEndTick(MinecraftClient client) {
                    if (done || !(client.currentScreen instanceof TitleScreen)) return;
                    done = true;
                    openMain();
                }
            });
        }

        McVerMod.LOGGER.info("mcver: 客户端初始化完成");
    }

    /** 主界面；MCVER_SCREEN=wizard|download 时直接进下载页。 */
    static void openMain() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UiState.dir();
        if (!Config.lastDir.isBlank()) {
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
            mc.inGameHud.getChatHud().addMessage(Text.literal(s));
        }
    }
}
