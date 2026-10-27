package dev.mcver.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;

/**
 * 1.12.2（Legacy Fabric）零散版本差异。
 *
 * <ul>
 *   <li>注册表入口是 {@code Item.REGISTRY}（1.16~1.18 的 {@code Registry.ITEM}、1.19.3+ 的
 *       {@code Registries.ITEM} 都不存在）；</li>
 *   <li>{@code Session#getUuid()} 直接返回字符串（没有 {@code getUuidOrNull}）；</li>
 *   <li>{@code Identifier} 只有 {@code new Identifier(ns, path)}。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    public static Identifier itemId(Item item) {
        return Item.REGISTRY.getIdentifier(item);
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
