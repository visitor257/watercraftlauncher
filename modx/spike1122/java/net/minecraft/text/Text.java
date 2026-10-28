package net.minecraft.text;

/**
 * 1.12.2 的「假名字」类，用于验证一个前提：
 * <b>Forge 1.12.2 的 LaunchClassLoader 允许 mod jar 里出现 {@code net.minecraft.*} 的类吗？</b>
 *
 * <p>如果允许，1.12.2 那代就能靠一小层「1.14 时代名字 → 1.12.2 SRG」的垫片，
 * 让共享的页面代码零改动地编过去；如果不允许，就得整份重写 UI。
 *
 * <p>注意：1.12.2 没有 {@code net.minecraft.text} 这个包（那是 1.13+ 的），
 * 所以这里不存在和原版撞名的风险。
 */
public final class Text {

    private final String s;

    private Text(String s) {
        this.s = s;
    }

    public static Text literal(String s) {
        return new Text(s == null ? "" : s);
    }

    public String getString() {
        return s;
    }

    @Override
    public String toString() {
        return s;
    }
}
