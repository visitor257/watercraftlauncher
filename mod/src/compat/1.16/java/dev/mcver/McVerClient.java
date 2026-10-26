package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import dev.mcver.ui.DownloadPage;
import dev.mcver.ui.Txt;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.itemgroup.FabricItemGroupBuilder;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.client.ClientTickCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

/**
 * 1.16 系列（1.16 ~ 1.16.5）兼容层：客户端入口。
 *
 * <p>跟 1.19.4 那份比的差异：
 * <ul>
 *   <li>{@code Text.literal} 没有，聊天栏文本走 {@code new LiteralText}（由 {@link dev.mcver.ui.Txt} 代劳）；</li>
 *   <li>创造栏：没有 {@code ItemGroupEvents}，只能用 {@code FabricItemGroupBuilder} 自建一个栏；</li>
 *   <li>{@code MinecraftClient#setScreen} 在 1.16 叫 {@code openScreen}；</li>
 *   <li>{@code ItemStack#isOf(Item)} 是 1.17 才有的，这里用 {@code getItem() == item}。</li>
 * </ul>
 *
 * <p><b>为什么用 v0 的 {@link ClientTickCallback} 而不是 v1 的 {@code ClientTickEvents}</b>：
 * 1.16 能用的 fabric-api 只有 <b>0.13.1</b>（Modrinth 上唯一一个把 1.16 写进 game_versions 的），
 * 它里面只有 {@code fabric-events-lifecycle-v0}，没有 v1 那个模块 → 用 v1 的 API 直接
 * {@code NoClassDefFoundError: ClientTickEvents}（实测就在 1.16 上炸的）。
 * 而 v0 的 {@code ClientTickCallback} 从 0.13.1 到 0.42.0 一直在、签名一字不差
 * （{@code void tick(MinecraftClient)}）—— 所以一个 jar 能盖住整个 1.16 系列。
 */
public class McVerClient implements ClientModInitializer {

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        // ---- 创造栏：自建一个（1.16.5 塞不进原版 TOOLS 栏，没有 ItemGroupEvents）
        FabricItemGroupBuilder.create(new Identifier(McVerMod.MOD_ID, "items"))
                .icon(() -> new ItemStack(McVerMod.DOWNLOAD_TOOL))
                .appendItems(stacks -> stacks.add(new ItemStack(McVerMod.DOWNLOAD_TOOL)))
                .build();

        // ---- 手持「版本下载器」右键
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.getItem() == McVerMod.DOWNLOAD_TOOL) {
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

        ClientTickCallback.EVENT.register(client -> {
            while (openKey.wasPressed()) {
                if (client.player == null) break;
                openMain();
            }
        });

        // ---- 自检钩子：进标题界面就自动打开
        if (System.getenv("MCVER_OPEN_GUI") != null) {
            ClientTickCallback.EVENT.register(new ClientTickCallback() {
                private boolean done;
                @Override
                public void tick(MinecraftClient client) {
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
        mc.openScreen(screen);
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
