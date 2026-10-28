package dev.mcver.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 零散版本差异的小适配层（mojmap 版，MC 1.17.1）。
 *
 * <p>跟主源码那份相比：
 * <ul>
 *   <li>{@code Registries} 类是 1.19.3 才有的，1.17.1 用 {@code Registry.ITEM.getKey(item)}；</li>
 *   <li>会话对象叫 {@code User}（1.19 起才叫 {@code Session}），uuid 是
 *       {@code getUser().getUuid()} 直接给字符串（{@code getUuidOrNull} 是 1.19 的写法）。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static ResourceLocation itemId(Item item) {
        return Registry.ITEM.getKey(item);
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        String uuid = Minecraft.getInstance().getUser().getUuid();
        return uuid == null ? null : uuid.replace("-", "");
    }

    /** 会话里的玩家名（正版/离线都行）。 */
    public static String sessionName() {
        return Minecraft.getInstance().getUser().getName();
    }

    /** 会话里的 access token。 */
    public static String sessionToken() {
        return Minecraft.getInstance().getUser().getAccessToken();
    }
}
