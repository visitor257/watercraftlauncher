package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;

/**
 * 1.7.10 零散版本差异。
 *
 * <p>1.7 的 {@code Item.REGISTRY} 是 <b>raw 的 SimpleRegistry</b>（没有泛型）：
 * 键是字符串、id 是 int，{@code getId(Object)} 直接返回 {@code "minecraft:apple"} 这样的字符串 ——
 * 所以 {@link #itemId} 返回 {@link String} 而不是 {@link net.minecraft.util.Identifier}
 * （调用点接的是 {@code Object}，不受影响）。
 */
public final class Compat {

    private Compat() { }

    public static String itemId(Item item) {
        try {
            return item == null ? null : Item.REGISTRY.getId(item);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String sessionUuid() {
        String uuid = MinecraftClient.getInstance().getSession().getUuid();
        return uuid == null ? null : uuid.replace("-", "");
    }

    public static String sessionName() {
        return MinecraftClient.getInstance().getSession().getUsername();
    }

    public static String sessionToken() {
        return MinecraftClient.getInstance().getSession().getAccessToken();
    }
}
