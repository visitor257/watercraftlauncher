package dev.mcver.core;

/**
 * 零散版本差异的小适配层（MC 1.9.4 / SRG 命名空间）。
 *
 * <p>跟 1.12.2 那份的差别：{@code IForgeRegistryEntry} 还在
 * {@code net.minecraftforge.fml.common.registry}（1.12 才挪到 {@code net.minecraftforge.registries}）。
 * 会话对象同样是 {@code net.minecraft.util.Session}，SRG 名字也一样。
 */
public final class Compat {

    private Compat() { }

    public static net.minecraft.util.ResourceLocation itemId(Object item) {
        return ((net.minecraftforge.fml.common.registry.IForgeRegistryEntry<?>) (Object) item).getRegistryName();
    }

    /** 会话里的玩家 uuid，去掉横线的那种；拿不到就 null。 */
    public static String sessionUuid() {
        String uuid = net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_148255_b();
        return uuid == null ? null : uuid.replace("-", "");
    }

    /** 会话里的玩家名。 */
    public static String sessionName() {
        return net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_111285_a();
    }

    /** 会话里的 access token。 */
    public static String sessionToken() {
        return net.minecraft.client.Minecraft.func_71410_x().func_110432_I().func_148254_d();
    }
}
