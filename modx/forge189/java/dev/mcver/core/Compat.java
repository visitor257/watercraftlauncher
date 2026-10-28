package dev.mcver.core;

/**
 * 零散版本差异的小适配层（MC 1.8.9 / SRG 命名空间）。
 *
 * <p>1.8.9 没有 {@code ForgeRegistries} / {@code IForgeRegistryEntry}（1.9 才有），
 * 物品名只能走 Forge 的老 API {@code GameRegistry.findUniqueIdentifierFor(item)}。
 * 会话对象同样是 {@code net.minecraft.util.Session}，SRG 名字也一样。
 */
public final class Compat {

    private Compat() { }

    /** 物品的注册名（{@code modid:name}）；查不到就退到 unlocalized name。 */
    public static Object itemId(Object item) {
        if (item instanceof net.minecraft.item.Item) {
            net.minecraft.item.Item it = (net.minecraft.item.Item) item;
            net.minecraftforge.fml.common.registry.GameRegistry.UniqueIdentifier id =
                    net.minecraftforge.fml.common.registry.GameRegistry.findUniqueIdentifierFor(it);
            return id == null ? it.func_77658_a() : id.toString();
        }
        return null;
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
