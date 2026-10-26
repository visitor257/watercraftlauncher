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

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        var session = MinecraftClient.getInstance().getSession();
        var uuid = session.getUuidOrNull();
        return uuid == null ? null : uuid.toString().replace("-", "");
    }
}
