package dev.mcver.core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * 零散版本差异的小适配层（mojmap 版）。
 *
 * <p>两处差异：
 * <ul>
 *   <li>yarn {@code Registries} → mojmap {@code BuiltInRegistries}；</li>
 *   <li>yarn {@code Session#getUuidOrNull()} → mojmap {@code Minecraft#getUser().getProfileId()}。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        java.util.UUID uuid = Minecraft.getInstance().getUser().getProfileId();
        return uuid == null ? null : uuid.toString().replace("-", "");
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
