package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 1.13.2 兼容层：零散版本差异。
 *
 * <ul>
 *   <li>注册表已经是 {@code net.minecraft.util.registry.Registry}（1.13 新重构的），
 *       <b>但它还没有静态 {@code register(...)}</b>（1.14+ 才有）→ 只能
 *       {@code Registry.ITEM.add(id, item)}；</li>
 *   <li>{@code Registry#getId(T)} 有，能拿回物品 id；</li>
 *   <li>{@code Session#getUuid()} 直接返回字符串（1.19 才有 getUuidOrNull）。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Registry.ITEM.getId(item);
    }

    /** 会话里的玩家 uuid，去掉横线；拿不到就 null。 */
    public static String sessionUuid() {
        net.minecraft.client.util.Session session = MinecraftClient.getInstance().getSession();
        String uuid = session == null ? null : session.getUuid();
        return uuid == null ? null : uuid.replace("-", "");
    }

    /** 会话里的玩家名（正版/离线都行）。 */
    public static String sessionName() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    /** 会话里的 access token。 */
    public static String sessionToken() {
        return MinecraftClient.getInstance().getSession().getAccessToken();
    }
}
