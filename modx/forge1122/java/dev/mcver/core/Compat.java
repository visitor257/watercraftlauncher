package dev.mcver.core;

/**
 * 零散版本差异的小适配层（MC 1.12.2 / SRG 命名空间）。
 *
 * <p>1.12.2 的会话对象是 {@code net.minecraft.util.Session}（1.16+ 叫 {@code User}，
 * 1.19+ 叫 {@code Session}），而且 uuid 是 {@code func_148255_b()}、名字是
 * {@code func_111285_a()}、token 是 {@code func_148254_d()}。
 *
 * <p>物品 id：1.12.2 没有 {@code Registry.ITEM} / {@code BuiltInRegistries}，直接用
 * Forge 的 {@code IForgeRegistryEntry#getRegistryName()}（这个方法名没被混淆，因为它来自
 * Forge 的接口）。
 */
public final class Compat {

    private Compat() { }

    public static net.minecraft.util.ResourceLocation itemId(Object item) {
        return ((net.minecraftforge.registries.IForgeRegistryEntry<?>) (Object) item).getRegistryName();
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        String uuid = net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_148255_b();
        return uuid == null ? null : uuid.replace("-", "");
    }

    /** 会话里的玩家名（正版/离线都行）。 */
    public static String sessionName() {
        return net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_111285_a();
    }

    /** 会话里的 access token。 */
    public static String sessionToken() {
        return net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_148254_d();
    }
}
