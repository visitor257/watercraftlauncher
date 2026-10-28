package net.minecraft.world.item;

/**
 * 1.10.2 垫片：1.13+ 的 {@code net.minecraft.world.item.ItemStack}（1.10.2 在
 * {@code net.minecraft.item.ItemStack}）。
 *
 * <p>跟 1.12.2 那份唯一的差别是 {@code isEmpty()}：1.10.2 <b>没有</b>
 * {@code func_190926_b}（Capability 是 1.11 才加的），「空」就是 null。
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
        return handle == null;
    }

    public net.minecraft.item.Item getItem() {
        return handle.func_77973_b();
    }
}
