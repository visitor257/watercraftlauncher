package dev.mcver.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * 1.13.2（Legacy Fabric 段）屏幕基类。
 *
 * <p>1.13 已经做了 GUI 重构（{@code Element} / {@code Focusable} 都出来了），但和 1.14 还差一点：
 * <ul>
 *   <li>构造器 <b>不收标题</b>（1.14 才收 {@code Text}）→ 标题自己存 {@link #screenTitle}；</li>
 *   <li>{@code render(int mouseX, int mouseY, float delta)}，<b>而且它不会自己调 renderBackground</b>
 *       （只遍历 {@code buttons} 画按钮）→ 面板必须手动先画；</li>
 *   <li>控件列表是两个：{@code buttons}（会被 render 画）和 {@code field_20307}（会被点击/键盘分发）。
 *       {@code addButton()} 两个都塞；输入框不进 {@code buttons}，所以<b>得自己 render/tick</b>，
 *       但为了能收到点击和键盘，仍要手动放进 {@code field_20307}；</li>
 *   <li>没有 {@code mouseScrolled(double mouseX, double mouseY, ...)}，只有 Element 那版单参
 *       {@code mouseScrolled(double vertical)} → 鼠标坐标自己记（render 时更新）；</li>
 *   <li>{@code onClose} 没被 yarn 命名，是 {@code method_18608()}；{@code shouldCloseOnEsc}
 *       是 {@code method_18607()}；</li>
 *   <li>{@code keyPressed(int,int,int)} 已经有了（不用像 1.12 那样收 char）。</li>
 * </ul>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏幕。 */
    protected Screen parentScreen;

    /** 1.13 的 {@code Screen} 构造函数不收标题，标题自己存。 */
    protected Text screenTitle;

    /** 输入框：{@code buttons} 列表不收它们，所以自己管渲染和 tick。 */
    private final List<TextFieldWidget> fields = new ArrayList<>();

    /** 上一帧的鼠标位置（{@code mouseScrolled(double)} 拿不到坐标）。 */
    private double lastMouseX;
    private double lastMouseY;

    protected ChromeScreen(Text title) {
        super();
        this.screenTitle = title;
    }

    /** 面板底、标题栏、导航分割线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。1.13 没有横向滚动，horizontal 恒为 0。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    /** 加控件：按钮进 {@code buttons}，输入框进自己的表 + 元素表。 */
    protected void addWidget(Object w) {
        if (w instanceof ButtonWidget) {
            addButton((ButtonWidget) w);
        } else if (w instanceof TextFieldWidget) {
            TextFieldWidget f = (TextFieldWidget) w;
            fields.add(f);
            // field_20307 = 元素表，点击/焦点分发靠它；protected 字段，子类可直接用
            this.field_20307.add(f);
        }
    }

    /** 清控件：1.13 没有 clearChildren()，两个表都得清。 */
    protected void clearWidgets() {
        buttons.clear();
        labels.clear();
        this.field_20307.clear();
        fields.clear();
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        // 1.13 的 Screen.render 只画 buttons，不会先画背景 → 手动来，否则整块面板是透的
        renderBackground();
        super.render(mouseX, mouseY, delta);
        for (int i = 0; i < fields.size(); i++) fields.get(i).method_18385(mouseX, mouseY, delta);
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

    /** 滚轮：1.13 只有 Element 的单参版本（纯竖直量）。 */
    @Override
    public boolean mouseScrolled(double vertical) {
        if (onScroll(this.lastMouseX, this.lastMouseY, 0, vertical)) return true;
        return super.mouseScrolled(vertical);
    }

    /** 关掉自己并回父屏（UI 代码统一调这个，不碰版本钩子名）。 */
    public void closeSelf() {
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    /** 1.13 的 onClose 没被 yarn 命名，叫 {@code method_18608()}。 */
    @Override
    public void method_18608() {
        closeSelf();
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    /** 1.13 的 shouldCloseOnEsc 没被命名，叫 {@code method_18607()}。 */
    protected boolean shouldCloseOnEsc() {
        return true;
    }

    /** 1.13 没有 {@code children()}；UI 层只用它数控件（日志里那行 children=）。 */
    protected List<Object> children() {
        List<Object> all = new ArrayList<>(buttons.size() + fields.size());
        all.addAll(buttons);
        all.addAll(fields);
        return all;
    }

    /** 键盘：UI 层只 override 这个。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyPressedDefault(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 页面代码里用 {@code tr()} 量字，这里给个等价入口（1.13 没有 Page 那份）。 */
    protected TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    /** 兼容 1.16+ 的写法：页面里偶尔直接 {@code Field.changed(f, ...)}。 */
    protected static void onChanged(TextFieldWidget f, Consumer<String> c) {
        Field.changed(f, c);
    }
}
