package dev.mcver.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.input.Mouse;

/**
 * 1.12.2（Legacy Fabric）屏幕基类。
 *
 * <p>和 1.15 的差别：
 * <ul>
 *   <li>{@code render(int,int,float)} 一样没有 MatrixStack，但渲染前要手动 {@code renderBackground()}
 *       （1.12 的 {@code Screen#render} 只遍历控件）；</li>
 *   <li>点击不分发回调，靠 {@code buttonClicked(ButtonWidget)} + {@link Btn} 的 id 表；</li>
 *   <li><b>没有 {@code mouseScrolled}</b>：滚轮要在 {@code handleMouse()} 里读 LWJGL2 的
 *       {@code Mouse.getDWheel()}；</li>
 *   <li>键盘是 {@code keyPressed(char, int)}；</li>
 *   <li><b>{@code Screen} 只有 {@code buttons}（ButtonWidget），输入框不在这套列表里</b> ——
 *       1.12 没有 {@code AbstractButtonWidget}/{@code children}，所以输入框自己管：
 *       存 {@link #fields}，自己调 {@code render()/tick()/keyPressed}。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏幕。 */
    protected Screen parentScreen;

    /** 1.12 的 {@code Screen} 构造器不收标题，标题自己存。 */
    protected Text screenTitle;

    /** 输入框单独管理（1.12 的 buttons 列表只收 ButtonWidget）。 */
    private final List<TextFieldWidget> fields = new ArrayList<>();

    private int lastWheel;

    protected ChromeScreen(Text title) {
        super();
        this.screenTitle = title;
    }

    /** 面板底、标题栏、导航分割线。 */
    protected abstract void drawChrome(Ctx ctx);

    protected void addControl(Object w) {
        if (w instanceof ButtonWidget) {
            // 1.9~1.10 没有 addButton(T)，只有 buttons 列表
            buttons.add((ButtonWidget) w);
        } else if (w instanceof TextFieldWidget) {
            fields.add((TextFieldWidget) w);
        }
    }

    protected void clearWidgets() {
        buttons.clear();
        labels.clear();
        fields.clear();
        Field.clear();
    }

    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        renderBackground();
        super.render(mouseX, mouseY, delta);
        for (int i = 0; i < fields.size(); i++) fields.get(i).render();
        Field.poll();
        renderContent(Ctx.of(), mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
        drawChrome(Ctx.of());
    }

    @Override
    public void tick() {
        super.tick();
        for (int i = 0; i < fields.size(); i++) fields.get(i).tick();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        for (int i = 0; i < fields.size(); i++) fields.get(i).mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (!Btn.dispatch(button)) super.buttonClicked(button);
    }

    /** 滚轮：LWJGL2 的累计值，取增量后分发（1.12 没有 mouseScrolled 钩子）。 */
    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getDWheel();
        int delta = wheel - lastWheel;
        lastWheel = wheel;
        if (delta == 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int dw = org.lwjgl.opengl.Display.getWidth();
        int scale = mc.width <= 0 ? 1 : Math.max(1, (int) Math.round(dw / (double) mc.width));
        onScroll(Mouse.getX() / (double) scale, (mc.height - Mouse.getY()) / (double) scale, 0, delta / 120.0);
    }

    public void closeSelf() {
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    /** 1.12 没有 {@code onClose}，关闭走 {@code removed()}。 */
    @Override
    public void removed() {
        closeSelf();
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    /** 1.12 没有 {@code shouldCloseOnEsc}（ESC 在父类直接关）；UI 层当它能关就行。 */
    protected boolean shouldCloseOnEsc() {
        return true;
    }

    /** 1.12 没有 {@code children()}；UI 层只用它数控件。 */
    protected List<Object> children() {
        List<Object> all = new ArrayList<>(buttons.size() + fields.size());
        all.addAll(buttons);
        all.addAll(fields);
        return all;
    }

    /** 键盘：UI 层只 override 这个（1.12 是 char,int，且返回 void）。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    protected void keyPressed(char typedChar, int keyCode) {
        for (int i = 0; i < fields.size(); i++) {
            TextFieldWidget f = fields.get(i);
            if (f.isFocused() && f.keyPressed(typedChar, keyCode)) return;
        }
        if (!keyPressedDefault(keyCode, 0, 0)) super.keyPressed(typedChar, keyCode);
    }
}
