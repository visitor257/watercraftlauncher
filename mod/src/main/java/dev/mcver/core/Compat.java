package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * 零散版本差异的小适配层（跟 UI 无关的那些）。
 *
 * <p>两处：
 * <ul>
 *   <li>{@code itemId}：1.19.3 起注册表入口是 {@code net.minecraft.registry.Registries}，
 *       1.18.2 及更早是 {@code net.minecraft.util.registry.Registry}；</li>
 *   <li>{@code sessionUuid}：{@code Session#getUuidOrNull()} 是 1.19 才加的，
 *       1.18.2 的 {@code Session#getUuid()} 直接返回字符串。
 *       Session 这个类本身在 1.21 还搬过包（{@code client.util} → {@code client.session}），
 *       所以这里一律用 {@code var}，不写死类名。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    /**
     * 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。
     *
     * <p>不写局部变量：Session 的包名跳版本改过（1.20.1 是 {@code client.util}，1.21 起是
     * {@code client.session}），而主源码要在所有 target 上共用，Java 8 又没有 {@code var}。
     * 用链式调用绕开具体类型。
     */
    public static String sessionUuid() {
        java.util.UUID uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
        return uuid == null ? null : uuid.toString().replace("-", "");
    }

    /** 会话里的玩家名（正版/离线都行）。同样绕开 Session 的具体类型。 */
    public static String sessionName() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    /** 会话里的 access token。 */
    public static String sessionToken() {
        return MinecraftClient.getInstance().getSession().getAccessToken();
    }

    /** 存一张截图（自检用）。1.16.5 那边类名和参数都不一样，见 compat 那份。 */
    public static void saveScreenshot(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(
                mc.runDirectory, name, mc.getFramebuffer(), t -> { });
    }
}
