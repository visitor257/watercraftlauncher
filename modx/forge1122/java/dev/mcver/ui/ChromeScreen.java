package dev.mcver.ui;

import dev.mcver.bridge.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本差异层（MC 1.12.2 / Forge 14.23.x）。
 *
 * <p>1.12.2 是「PoseStack 之前、连 widget 基类都还没有」那一代：
 * <ul>
 *   <li>渲染入口 {@code func_73863_a(int mouseX, int mouseY, float delta)}，
 *       <b>而且它自己不画背景</b>（1.13+ 才有 renderBackground 的概念）；</li>
 *   <li>背景是 {@code func_146276_q_()}（drawDefaultBackground，画泥巴底）——
 *       我们不要泥巴，整块面板由 {@link #drawChrome} 铺满；</li>
 *   <li>按钮进 {@code field_146292_n}，靠 {@code func_189646_b} 添加；</li>
 *   <li>输入框（{@code GuiTextField}）<b>不进那个列表</b>：得自己存一份、自己画、自己转键盘；</li>
 *   <li>没有滚轮回调 —— 要覆盖 {@code func_146274_d()}（handleMouseInput）读
 *       {@code Mouse.getEventDWheel()}；</li>
 *   <li>键盘是 {@code func_73869_a(char typedChar, int keyCode)}，ESC = 1。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    /** 输入框：1.12.2 的 GuiScreen 不会自己画/转键盘，只能自己管。 */
    private final java.util.List<net.minecraft.client.gui.GuiTextField> textFields = new java.util.ArrayList<net.minecraft.client.gui.GuiTextField>();

    protected ChromeScreen(Component title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。按钮由 {@code super.func_73863_a} 画完，这里补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.12.2 只看纵向。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void func_73863_a(int mouseX, int mouseY, float delta) {
        // 1.12.2 的 drawScreen 不画背景，所以面板在这里先铺。
        drawChrome(Ctx.of());
        super.func_73863_a(mouseX, mouseY, delta);
        // 输入框不在 buttonList 里，super 不会画它们。
        for (net.minecraft.client.gui.GuiTextField f : textFields) f.func_146194_f();
        renderContent(Ctx.of(), mouseX, mouseY, delta);
    }

    /** 不要泥巴底：我们的面板自己铺满。 */
    @Override
    public void func_146276_q_() { }

    /** 滚轮。1.12.2 没有 mouseScrolled 回调，只能在 handleMouseInput 里读。 */
    @Override
    public void func_146274_d() {
        super.func_146274_d();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel == 0) return;
        int mx = org.lwjgl.input.Mouse.getEventX() * this.width / this.field_146297_k.field_71443_c;
        int my = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.field_146297_k.field_71440_d - 1;
        onScroll(mx, my, 0.0, dWheel / 120.0);
    }

    /** 加一个控件。按钮进 buttonList，输入框进自己的表。 */
    protected void addControl(Object w) {
        if (w instanceof net.minecraft.client.gui.GuiButton) {
            // 1.12.2 的入口是 func_189646_b（addButton 是 1.13+ 的名字）
            func_189646_b((net.minecraft.client.gui.GuiButton) w);
        } else if (w instanceof net.minecraft.client.gui.GuiTextField) {
            textFields.add((net.minecraft.client.gui.GuiTextField) w);
        } else if (w instanceof AbstractWidget) {
            // 目前没有第三种控件
        }
    }

    /** 清空控件。1.12.2 没有 clearWidgets()。 */
    protected void clearWidgets() {
        this.field_146292_n.clear();
        this.textFields.clear();
    }

    /**
     * 鼠标点击：先给输入框（它靠这次点击决定焦点），再交给按钮。
     *
     * <p><b>这一条以前漏了，后果是「输入框永远打不进字」</b>：1.12.2 的
     * {@code GuiScreen} 只把点击分发给 {@code field_146292_n}（按钮列表），
     * 而 {@code GuiTextField} 不在那个列表里（见 {@link #addControl}）。
     * 没人调它的 {@code func_146192_a} → {@code func_146206_l()}（isFocused）
     * 一直是 false → 上面 {@link #func_73869_a} 里那个焦点判断永远不成立，
     * 打字全被吞掉，而且不报任何错。
     */
    @Override
    public void func_73864_a(int mouseX, int mouseY, int button) {
        for (net.minecraft.client.gui.GuiTextField f : textFields) {
            f.func_146192_a(mouseX, mouseY, button);
        }
        dbg("click x=" + mouseX + " y=" + mouseY + " button=" + button
                + " fields=" + textFields.size() + " focused=" + focusedCount());
        super.func_73864_a(mouseX, mouseY, button);
    }

    private int focusedCount() {
        int n = 0;
        for (net.minecraft.client.gui.GuiTextField f : textFields) if (f.func_146206_l()) n++;
        return n;
    }

    /** 输入链路排查：设 {@code MCVER_DEBUG_INPUT=1} 时把点击/按键写进 mcver-debug.txt。 */
    private static void dbg(String msg) {
        if (System.getenv("MCVER_DEBUG_INPUT") == null) return;
        try {
            dev.mcver.core.Jdk.writeAppend(java.nio.file.Paths.get("mcver-debug.txt").toAbsolutePath(),
                    "input: " + msg);
        } catch (Throwable ignored) { }
    }

    @Override
    public void func_73869_a(char typedChar, int keyCode) {
        dbg("key c=" + (int) typedChar + " k=" + keyCode + " focused=" + focusedCount());
        // 输入框优先吃键盘（它自己判断有没有焦点）
        for (net.minecraft.client.gui.GuiTextField f : textFields) {
            if (f.func_146206_l() && f.func_146201_a(typedChar, keyCode)) return;
        }
        if (keyPressedDefault(keyCode, 0, 0)) return;
        super.func_73869_a(typedChar, keyCode);
    }

    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 1 && this.shouldCloseOnEsc()) { // ESC
            this.closeSelf();
            return true;
        }
        return false;
    }

    /** 关掉自己并回父屏。业务代码统一调这个。 */
    public void closeSelf() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    /**
     * 控件总数（自检用）。1.12.2 的输入框不在 {@code field_146292_n} 里
     * （{@code GuiScreen} 根本不管它们），所以这里合并成一份 —— 否则 debug Dump 里的
     * {@code children} 会比其它 target 少一个，验收判据就没法横向比。
     */
    @Override
    public java.util.List<?> children() {
        java.util.List<Object> all = new java.util.ArrayList<Object>(this.field_146292_n);
        all.addAll(textFields);
        return all;
    }
}
