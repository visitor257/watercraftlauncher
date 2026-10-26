package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 1.19 ~ 1.19.2 兼容层：零散版本差异。
 *
 * <p>跟主源码那份相比只有一处：
 * 注册表入口是 {@code net.minecraft.util.registry.Registry}（{@code Registries} 类 1.19.3 才有），
 * 物品 id 用 {@code Registry.ITEM.getId(item)}。
 *
 * <p>会话的 uuid/名字/token 跟主源码一样（1.19 已经有 {@code getUuidOrNull()} 之类的新名，
 * 但这里保持老写法，反正两者在 1.19 都能用）。
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Registry.ITEM.getId(item);
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        net.minecraft.client.util.Session session = MinecraftClient.getInstance().getSession();
        String uuid = session.getUuid();
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
