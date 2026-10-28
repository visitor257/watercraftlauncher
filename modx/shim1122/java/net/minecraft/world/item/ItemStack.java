package net.minecraft.world.item;

/**
 * 1.12.2 垫片：1.13+ 的 {@code net.minecraft.world.item.ItemStack}（1.12.2 在
 * {@code net.minecraft.item.ItemStack}）。
 *
 * <p>页面代码只在 {@code ctx.drawItem(...)} 和构造里用到它，所以包一层即可。
 */
public final class ItemStack {

    private final net.minecraft.item.ItemStack handle;

    public ItemStack(net.minecraft.item.Item item) {
        this.handle = new net.minecraft.item.ItemStack(item);
    }

    public ItemStack(net.minecraft.item.ItemStack stack) {
        this.handle = stack;
    }

    public net.minecraft.item.ItemStack unwrap() {
        return handle;
    }

    public boolean isEmpty() {
        return handle.func_190926_b();
    }

    /** 1.14+ 的 {@code getItem()}，页面拿它去查 registry id。 */
    public net.minecraft.item.Item getItem() {
        return handle.func_77973_b();
    }
}
