package dev.mcver.core;

/**
 * 零散版本差异的小适配层（MC 1.7.10 / SRG 命名空间）。
 *
 * <p>1.7.10 跟 1.12.2 的差别：
 * <ul>
 *   <li>会话对象同样是 {@code net.minecraft.util.Session}，uuid/名字/token 的 SRG 名字也一样；</li>
 *   <li><b>没有 {@code ForgeRegistries}</b>（1.8 才有），也没有
 *       {@code IForgeRegistryEntry}；物品名从老的 {@code RegistryNamespaced} 里查，或者直接
 *       用 {@code func_77658_a()}（getUnlocalizedName）。</li>
 * </ul>
 */
public final class Compat {

    private Compat() { }

    /**
     * 物品的注册名（{@code modid:name}）。1.7.10 没有 registry 对象，用 Forge 的
     * {@code GameRegistry.findUniqueIdentifierFor(item)}；拿不到就退到 unlocalized name。
     * （注意：老的 {@code Item.field_150901_e.func_148757_b} 返回的是**数字 id**，不是名字。）
     */
    public static Object itemId(Object item) {
        if (item instanceof net.minecraft.item.Item) {
            net.minecraft.item.Item it = (net.minecraft.item.Item) item;
            cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier id =
                    cpw.mods.fml.common.registry.GameRegistry.findUniqueIdentifierFor(it);
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
