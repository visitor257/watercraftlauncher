package net.minecraft.world.item;

/**
 * 1.12.2 垫片：1.13+ 的 {@code net.minecraft.world.item.Items}（1.12.2 在
 * {@code net.minecraft.init.Items}）。
 *
 * <p>页面里只用到 {@code Items.PAPER}（定标对照用的第二张图标），所以只导出一个字段。
 * 用 id 查表而不是硬编码 SRG 字段名 —— 字段名如果记错会静默变成 null，查表至少逻辑清楚。
 */
public final class Items {

    private Items() { }

    public static final net.minecraft.item.Item PAPER = net.minecraft.item.Item.func_111206_d("minecraft:paper");
}
