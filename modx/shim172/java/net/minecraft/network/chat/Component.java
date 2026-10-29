package net.minecraft.network.chat;

/**
 * 1.7.10 的「假名字」垫片：1.13+ 的 {@code Component} → 1.7.10 的
 * {@code net.minecraft.util.IChatComponent}。
 *
 * <p>{@code net.minecraft.network.chat} 这个包在 1.7.10 里不存在（那时叫
 * {@code net.minecraft.util}），所以往 mod jar 里放这个类不会跟原版撞名。
 *
 * <p>1.7.10 具体实现是 {@code ChatComponentText}（1.12.2 叫 {@code TextComponentString}）；
 * {@code getString()} 对应 {@code func_150260_c()}（getUnformattedText）。
 */
public class Component {

    private final net.minecraft.util.IChatComponent handle;

    public Component(net.minecraft.util.IChatComponent handle) {
        this.handle = handle;
    }

    public static Component literal(String s) {
        return new Component(new net.minecraft.util.ChatComponentText(s == null ? "" : s));
    }

    public String getString() {
        return handle.func_150260_c();
    }

    public net.minecraft.util.IChatComponent unwrap() {
        return handle;
    }

    @Override
    public String toString() {
        return getString();
    }
}
