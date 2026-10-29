package dev.mcver;

import dev.mcver.core.Config;
import dev.mcver.ui.DownloadPage;
import dev.mcver.ui.MainScreen;
import dev.mcver.ui.UiState;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import dev.mcver.ui.Txt;

/**
 * 1.12.2（Legacy Fabric）客户端入口。
 *
 * <p><b>刻意不用 Fabric API</b>：Legacy Fabric 的 api 包里 {@code ClientTickEvents} 等都是
 * {@code net.legacyfabric.*} 且按 ornithe 的 {@code C_xxxx} 命名发布，跟本工程用的
 * {@code class_xxxx} intermediary 对不上。1.12 能用的原生钩子已经够：
 * <ul>
 *   <li>右键 → {@link McVerItem#method_13649}；</li>
 *   <li>自检/免按键开界面 → 本类里的轮询线程（只在 {@code MCVER_OPEN_GUI} 时启用）。</li>
 * </ul>
 */
public class McVerClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Config.load();

        if (System.getenv("MCVER_OPEN_GUI") != null) {
            startAutoOpenPoll();
        }

        McVerMod.LOGGER.info("mcver: 客户端初始化完成");
    }

    /**
     * 自检钩子：没有 tick 事件，起一个守护线程轮询，看到标题界面就把它换掉。
     *
     * <p><b>这里直接调 {@code openMain()} 而不是走 {@code core/Ui.exec}：</b>1.7.2 的
     * {@code MinecraftClient} 里根本没有收 Runnable/Callable 的方法（1.7.10 才有 {@code execute}），
     * {@code Ui.exec} 的队列只能由「我们自己的界面」的 {@code tick()} 抽取 ——
     * 而自检钩子恰恰是在界面还没开时就要动手，排进去就永远出不来（死锁）。
     * 1.7 的 {@code setScreen} 只是给字段赋值，主循环下一帧自然就会画，从这里调是安全的；
     * 而且这条路径只在 {@code MCVER_OPEN_GUI} 自检时走，不影响用户的真实右键路径。
     */
    private static void startAutoOpenPoll() {
        Thread t = new Thread(() -> {
            for (int i = 0; i < 6000; i++) { // 最多等 5 分钟（50ms 一次）—— 1.7 首次启动要刷 600 多个资源，1 分钟根本不够
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException e) {
                    return;
                }
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.currentScreen == null) continue;
                if (!(mc.currentScreen instanceof TitleScreen)) continue;
                openMain();
                return;
            }
        }, "mcver-autopen");
        t.setDaemon(true);
        t.start();
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
