package net.minecraft.network.chat;

/**
 * 1.12.2 的「假名字」垫片：把 1.13+ 的 {@code Component} 映射到 1.12.2 的
 * {@code net.minecraft.util.text.ITextComponent}。
 *
 * <p>{@code net.minecraft.network.chat} 这个包在 1.12.2 里<b>不存在</b>（1.12.2 叫
 * {@code net.minecraft.util.text}），所以往 mod jar 里放这个类不会跟原版撞名 —— 这是整套
 * 「1.14 时代代码名 → 1.12.2 SRG」垫片方案能成立的前提（已用 spike 验证 LaunchClassLoader
 * 愿意加载 mod jar 里的 {@code net.minecraft.*}）。
 *
 * <p>页面代码只把这些当<b>类型</b>用，真正的调用都收在 {@code Txt} / {@code Ctx} 里，
 * 所以垫片可以很薄。
 */
public class Component {

    private final net.minecraft.util.text.ITextComponent handle;

    public Component(net.minecraft.util.text.ITextComponent handle) {
        this.handle = handle;
    }

    public static Component literal(String s) {
        return new Component(new net.minecraft.util.text.TextComponentString(s == null ? "" : s));
    }

    /** 1.13+ 叫 getString()；1.12.2 是 {@code func_150260_c()}（不带格式的纯文本）。 */
    public String getString() {
        return handle.func_150260_c();
    }

    public net.minecraft.util.text.ITextComponent unwrap() {
        return handle;
    }

    @Override
    public String toString() {
        return getString();
    }
}
