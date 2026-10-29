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
 * 1.7.2（Legacy Fabric）屏幕基类。
 *
 * <p><b>和 1.7.10 的唯一实质差别：{@code Screen} 的成员在 1.7.2 的 yarn 里没名字。</b>
 * 对照过两代的 mapping 文件：1.7.2 的 {@code net/minecraft/client/gui/screen/Screen}
 * 一个成员都没映射（yarn build 604，已是最新），jar 里长这样：
 *
 * <pre>
 *   field_22535/22536 = width/height      field_22537/22538 = buttons/labels
 *   field_22540       = textRenderer      field_22533       = itemRenderer
 *   method_21925(IIF) = render            method_21946()    = renderBackground
 *   method_21936()    = tick              method_21929      = init(mc,w,h)
 *   method_21947()    = init()            method_21933()    = removed
 *   method_21926/35/27 = mouseClicked/Released/Dragged
 *   method_21930(ButtonWidget) = buttonClicked
 *   method_21938()    = handleMouse       method_21924(CI)  = keyPressed
 *   method_21940()    = shouldPauseGame   method_21939(String) = setClipboard
 *   method_21878(IIIII) = DrawableHelper.fill
 * </pre>
 *
 * 这些名字是靠 javap -c 逐个体检出来的（看方法体调了谁：{@code handleInput} 里是
 * {@code Mouse.next} + {@code Keyboard.next}，{@code renderBackground} 里 bindTexture 再画整屏……）。
 * 除 Screen 和 ButtonWidget 之外，1.7.2 的其余映射都是全的（TextFieldWidget / TextRenderer /
 * ItemRenderer / SimpleRegistry 跟 1.7.10 一模一样）。
 *
 * <p>因为主源码（MainScreen、各 Page）是按 yarn 名字写的、要跨 14 个 target 共用，
 * 这里做两件事把差异挡在基类里：
 * <ol>
 *   <li>{@code width/height/textRenderer} 在 1.7.2 的 Screen 上叫别的名字 → 本类自己声明这三个字段，
 *       每次 init/render/tick 从真字段同步过来。子类照旧用 {@code width/height/textRenderer}。</li>
 *   <li>{@code Screen#init()} 没名字 → 本类覆写 {@code method_21947()} 转调自己的 {@code init()}，
 *       子类的 {@code public void init()} 因此仍然生效。</li>
 * </ol>
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏幕。 */
    protected Screen parentScreen;

    /** 1.7 的 Screen 构造器不收标题，标题自己存。 */
    protected Text screenTitle;

    /** 输入框单独管理（buttons 列表只收 ButtonWidget）。 */
    private final List<TextFieldWidget> fields = new ArrayList<>();

    private int lastWheel;

    // ---- 影子字段：1.7.2 的 Screen 里叫 field_22535/22536/22540 ----
    public int width;
    public int height;
    protected TextRenderer textRenderer;

    protected ChromeScreen(Text title) {
        super();
        this.screenTitle = title;
    }

    private void syncShadow() {
        this.width = this.field_22535;
        this.height = this.field_22536;
        this.textRenderer = this.field_22540;
    }

    /** 1.7.2：{@code Screen#init(mc,w,h)} = method_21929，它会清空 buttons 再调 init()。 */
    @Override
    public void method_21929(MinecraftClient mc, int w, int h) {
        super.method_21929(mc, w, h);
        syncShadow();
    }

    /** 1.7.2：{@code Screen#init()} = method_21947。子类覆写的是下面这个 {@code init()}。 */
    @Override
    public void method_21947() {
        super.method_21947();
        syncShadow();
        this.init();
    }

    /** 子类（MainScreen 等）覆写这个。 */
    public void init() { }

    /** 面板底、标题栏、导航分割线。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 1.7 的 ItemRenderer 是 Screen 上的 protected static 字段（field_22533），只能由子类吐出来。 */
    static net.minecraft.client.render.item.ItemRenderer itemRendererOf() {
        return field_22533;
    }

    protected void addControl(Object w) {
        if (w instanceof ButtonWidget) {
            field_22537.add((ButtonWidget) w);
        } else if (w instanceof TextFieldWidget) {
            fields.add((TextFieldWidget) w);
        }
    }

    protected void clearWidgets() {
        field_22537.clear();
        field_22538.clear();
        fields.clear();
        Field.clear();
    }

    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void method_21925(int mouseX, int mouseY, float delta) {
        syncShadow();
        method_21946();                       // 1.7.2 的 render 只画控件，背景得自己先画
        super.method_21925(mouseX, mouseY, delta);
        for (int i = 0; i < fields.size(); i++) fields.get(i).render();
        Field.poll();
        renderContent(Ctx.of(), mouseX, mouseY, delta);
    }

    @Override
    public void method_21946() {              // renderBackground()
        super.method_21946();
        drawChrome(Ctx.of());
    }

    /** 1.7.2：{@code Screen#tick()} = method_21936。子类覆写的是下面这个 {@code tick()}（MainScreen 要它）。 */
    @Override
    public void method_21936() {
        super.method_21936();
        syncShadow();
        // 1.7.2 没有 MinecraftClient#execute，排队任务靠这里在客户端线程上抽取（见 core/Ui）
        dev.mcver.core.Ui.drain();
        for (int i = 0; i < fields.size(); i++) fields.get(i).tick();
        this.tick();
    }

    /** 子类（MainScreen）覆写这个。 */
    public void tick() { }

    @Override
    protected void method_21926(int mouseX, int mouseY, int button) {   // mouseClicked
        super.method_21926(mouseX, mouseY, button);
        for (int i = 0; i < fields.size(); i++) fields.get(i).mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void method_21930(ButtonWidget button) {                  // buttonClicked
        if (!Btn.dispatch(button)) super.method_21930(button);
    }

    /** 滚轮：LWJGL2 的累计值，取增量后分发（1.7 没有 mouseScrolled 钩子）。 */
    @Override
    public void method_21938() {              // handleMouse()
        super.method_21938();
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

    /** 1.7 没有 {@code onClose}，关闭走 {@code removed()}（method_21933）。 */
    @Override
    public void method_21933() {
        closeSelf();
    }

    @Override
    public boolean method_21940() {           // shouldPauseGame()
        return false;
    }

    /** 1.7 没有 {@code shouldCloseOnEsc}（ESC 在父类直接关）；UI 层当它能关就行。 */
    protected boolean shouldCloseOnEsc() {
        return true;
    }

    /** 1.7 没有 {@code children()}；UI 层只用它数控件。 */
    protected List<Object> children() {
        List<Object> all = new ArrayList<>(field_22537.size() + fields.size());
        all.addAll(field_22537);
        all.addAll(fields);
        return all;
    }

    /** 键盘：UI 层只 override 这个。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    protected void method_21924(char typedChar, int keyCode) {          // keyPressed(char,int)
        for (int i = 0; i < fields.size(); i++) {
            TextFieldWidget f = fields.get(i);
            if (f.isFocused() && f.keyPressed(typedChar, keyCode)) return;
        }
        if (!keyPressedDefault(keyCode, 0, 0)) super.method_21924(typedChar, keyCode);
    }
}
