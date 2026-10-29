package dev.mcver.ui;

import dev.mcver.bridge.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本差异层（MC 1.7.10 / Forge 10.13.x）。
 *
 * <p>1.7.10 跟 1.12.2 的 GUI 结构几乎同构（都是「没有 widget 基类、没有滚轮回调」那一代），
 * 差别只有三处：
 * <ul>
 *   <li>加按钮<b>没有</b> {@code func_189646_b}（1.8 才有），直接 {@code field_146292_n.add(...)}；</li>
 *   <li>输入框<b>没有</b> {@code GuiResponder}（1.8 才有），值变化只能每帧 {@code poll()}；</li>
 *   <li>染色的 drawString 只有 int 坐标版本（1.12.2 的 {@code func_175063_a} 收 float）。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏。 */
    protected Screen parentScreen;

    /** 输入框：1.7.10 的 GuiScreen 不会自己画/转键盘/转鼠标，只能自己管。 */
    private final java.util.List<net.minecraft.client.gui.GuiTextField> textFields = new java.util.ArrayList<net.minecraft.client.gui.GuiTextField>();

    protected ChromeScreen(Component title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.7.10 只看纵向。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void func_73863_a(int mouseX, int mouseY, float delta) {
        // 1.7.10 的 drawScreen 不画背景，面板在这里先铺。
        drawChrome(Ctx.of());
        super.func_73863_a(mouseX, mouseY, delta);
        // 输入框不在 buttonList 里，super 不会画它们；顺便把「值变了」的通知发出去。
        for (net.minecraft.client.gui.GuiTextField f : textFields) {
            if (f instanceof net.minecraft.client.gui.components.EditBox) {
                ((net.minecraft.client.gui.components.EditBox) f).poll();
            }
            f.func_146194_f();
        }
        renderContent(Ctx.of(), mouseX, mouseY, delta);
    }

    /** 不要泥巴底：我们的面板自己铺满。 */
    @Override
    public void func_146276_q_() { }

    /** 滚轮。1.7.10 没有 mouseScrolled 回调，只能在 handleMouseInput 里读。 */
    @Override
    public void func_146274_d() {
        super.func_146274_d();
        int dWheel = org.lwjgl.input.Mouse.getEventDWheel();
        if (dWheel == 0) return;
        int mx = org.lwjgl.input.Mouse.getEventX() * this.width / this.field_146297_k.field_71443_c;
        int my = this.height - org.lwjgl.input.Mouse.getEventY() * this.height / this.field_146297_k.field_71440_d - 1;
        onScroll(mx, my, 0.0, dWheel / 120.0);
    }

    /** 鼠标点击：先给输入框（决定焦点），再交给按钮。 */
    @Override
    public void func_73864_a(int mouseX, int mouseY, int button) {
        for (net.minecraft.client.gui.GuiTextField f : textFields) {
            f.func_146192_a(mouseX, mouseY, button);
        }
        super.func_73864_a(mouseX, mouseY, button);
    }

    /** 加一个控件。按钮进 buttonList，输入框进自己的表。 */
    protected void addControl(Object w) {
        if (w instanceof net.minecraft.client.gui.GuiButton) {
            this.field_146292_n.add(w);
        } else if (w instanceof net.minecraft.client.gui.GuiTextField) {
            textFields.add((net.minecraft.client.gui.GuiTextField) w);
        } else if (w instanceof AbstractWidget) {
            // 目前没有第三种控件
        }
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        this.field_146292_n.clear();
        this.textFields.clear();
    }

    @Override
    public void func_73869_a(char typedChar, int keyCode) {
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

    /** 关掉自己并回父屏。 */
    public void closeSelf() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    /**
     * 控件总数（自检用）。1.7.10 的输入框不在 {@code field_146292_n} 里，
     * 合并成一份，让 debug Dump 的 {@code children} 跟其它 target 可比。
     */
    @Override
    public java.util.List<?> children() {
        java.util.List<Object> all = new java.util.ArrayList<Object>(this.field_146292_n);
        all.addAll(textFields);
        return all;
    }
}
