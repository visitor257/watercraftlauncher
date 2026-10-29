package dev.mcver.ui;

/**
 * 给输入框设/收焦点。
 *
 * <p>为什么不直接 {@code f.setFocused(true)}：这个方法的可见性跨版本不一样 ——
 * 1.14 的 {@code AbstractButtonWidget#setFocused} 是 <b>protected</b>（直接调编不过），
 * 1.17+ 是 public；1.7~1.12 的 SRG 垫片里对应 {@code func_146195_b}（垫片另外包了
 * {@code setFocused}）。用反射按名字找，一套代码通吃。
 *
 * <p>为什么不放在 {@link Field} 里：{@code mod/src/compat/<版本>/} 会整文件覆盖
 * {@code Field.java}，加在那边等于每个 compat 都要复制一遍。这个类不在任何 compat 里，
 * 所有 target 共用。
 *
 * <p>为什么需要它：页面重建（{@code rebuild()}）会把输入框整个换成新实例，而焦点是
 * 控件实例自己的属性 —— 不还回去，用户只能往搜索框里打一个字（见
 * {@code DownloadPage.initList}）。
 */
public final class Focus {

    private Focus() { }

    /** 关闭/打开焦点。找不到方法就静默放弃（老版本本来就打不进字，也不会崩）。 */
    public static void apply(Object widget, boolean on) {
        if (widget == null) return;
        Class<?> c = widget.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod("setFocused", boolean.class);
                m.setAccessible(true);
                m.invoke(widget, Boolean.valueOf(on));
                return;
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            } catch (Throwable t) {
                return;
            }
        }
    }
}
