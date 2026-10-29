package net.minecraft.world.item;

/**
 * 1.7.10 垫片：1.13+ 的 {@code net.minecraft.world.item.Items}（1.7.10 在
 * {@code net.minecraft.init.Items}）。
 *
 * <p>1.7.10 没有 {@code Item.getByNameOrId}（那是 1.12 的
 * {@code func_111206_d}，在 1.7.10 里同 id 是 {@code setTextureName}），所以直接查注册表：
 * {@code Item.field_150901_e.func_82594_a("minecraft:paper")}。
 */
public final class Items {

    private Items() { }

    @SuppressWarnings("unchecked")
    public static final net.minecraft.item.Item PAPER =
            (net.minecraft.item.Item) net.minecraft.item.Item.field_150901_e.func_82594_a("minecraft:paper");
}
