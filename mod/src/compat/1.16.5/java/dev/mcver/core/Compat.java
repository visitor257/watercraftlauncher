package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 1.16.5 兼容层：零散版本差异。
 *
 * <p>跟主源码那份相比：
 * <ul>
 *   <li>注册表入口是 {@code net.minecraft.util.registry.Registry}（{@code Registries} 类 1.19.3 才有），
 *       物品 id 用 {@code Registry.ITEM.getId(item)}；</li>
 *   <li>{@code Session#getUuidOrNull()} 没有（1.19 才加），1.16.5 的 {@code Session#getUuid()}
 *       直接返回字符串。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Registry.ITEM.getId(item);
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        var session = MinecraftClient.getInstance().getSession();
        String uuid = session.getUuid();
        return uuid == null ? null : uuid.replace("-", "");
    }

    /**
     * 存一张截图（自检用）。1.16.5 的类叫 {@code ScreenshotUtils}，要自己传宽高，
     * 消费者参数是 {@code Consumer<Text>}。
     */
    public static void saveScreenshot(String name) {
        var mc = MinecraftClient.getInstance();
        net.minecraft.client.util.ScreenshotUtils.saveScreenshot(
                mc.runDirectory, name,
                mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight(),
                mc.getFramebuffer(), t -> { });
    }
}
