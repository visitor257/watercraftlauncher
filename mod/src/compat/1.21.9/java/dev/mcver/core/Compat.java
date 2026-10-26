package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 1.21.9 时代的零散版本差异（1.21.9 / 1.21.10 / 1.21.11 共用）。
 *
 * <p>和主源码只差 {@code saveScreenshot}：1.21.9 起在 Framebuffer 和回调之间多了一个 int
 * （截图缩放倍数，传 1 = 原尺寸），签名变成
 * {@code saveScreenshot(File, String, Framebuffer, int, Consumer<Text>)}。
 * 编译器的报错原文：
 * <pre>
 *   method ScreenshotRecorder.saveScreenshot(File,Framebuffer,Consumer&lt;Text&gt;) is not applicable
 *     (actual and formal argument lists differ in length)
 * </pre>
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    public static String sessionUuid() {
        java.util.UUID uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        return uuid == null ? null : uuid.toString().replace("-", "");
    }

    public static String sessionName() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    public static String sessionToken() {
        return MinecraftClient.getInstance().getSession().getAccessToken();
    }

    /** 存一张截图（自检用）。1.21.9 起多了第 4 个 int 参数：缩放倍数。 */
    public static void saveScreenshot(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                mc.runDirectory, name, mc.getFramebuffer(), 1, t -> { });
    }
}
