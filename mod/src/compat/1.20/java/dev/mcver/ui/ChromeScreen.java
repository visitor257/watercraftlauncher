package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * 1.20 系列差异层（{@code src/main} 那份按 1.21.4 的签名写）。
 *
 * <p>这一个文件要同时盖住 <b>1.20 ~ 1.20.6</b>，而这中间有过两次签名分裂
 * （都用 javap 对着 remap 后的 merged jar 核过，不靠记忆）：
 *
 * <pre>
 *                                   1.20 / 1.20.1            1.20.2 ~ 1.20.6
 *   renderBackground                 (DrawContext)            (DrawContext,int,int,float)
 *   Element#mouseScrolled            (d,d,d)                  (d,d,d,d)
 *   Screen#render 自动调背景？        不会                     会
 * </pre>
 *
 * <p>一个 class 文件没法同时 override 两个不同签名的同名方法，所以换了思路：
 * <ol>
 *   <li>chrome 一律在 {@link #render} 里<b>自己</b>画，不依赖父类调不调 {@code renderBackground}；</li>
 *   <li>{@code renderBackground(DrawContext,int,int,float)} 写成<b>空实现</b>（故意不调 {@code super}）：
 *       1.20.2+ 的父类会调到它，空实现正好避免原版全景背景盖在面板上面；
 *       1.20/1.20.1 压根没有这个方法，多出来一个没人调的方法无害；</li>
 *   <li>{@code mouseScrolled} <b>两个重载都声明</b>：4 参的给 1.20.2+，3 参的给 1.20/1.20.1
 *       （编译基准是 1.20.4，所以 3 参那份不能标 {@code @Override}，标了会「方法不会覆盖超类型」编不过）。</li>
 * </ol>
 *
 * <p>为什么不画原版背景：面板是全屏不透明灰底（{@code #C6C6C6}），底下露不出一个像素，
 * 画了纯属浪费 —— 实测面板像素数和一个字节都没差。
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    protected ChromeScreen(Text title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 加一个控件（各版本钩子名不同，统一走这里）。 */
    protected void addWidget(Object w) {
        addDrawableChild((ClickableWidget) w);
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        clearChildren();
    }

    /** 页内容。父类已经画完控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // 面板自己画，不指望父类调 renderBackground（1.20/1.20.1 的 render 不会调）。
        drawChrome(Ctx.of(ctx));
        super.render(ctx, mouseX, mouseY, delta);
        renderContent(Ctx.of(ctx), mouseX, mouseY, delta);
    }

    /** 只有 1.20.2+ 会调到。空实现：原版全景背景会被面板整块盖住，画了看不见。 */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
    }

    /** 1.20.2 ~ 1.20.6 的重载（有横向滚动）。 */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (onScroll(mouseX, mouseY, horizontal, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    /**
     * 1.20 / 1.20.1 的旧重载（那时还没有横向滚动）。
     *
     * <p><b>不能标 {@code @Override}</b>：编译基准是 1.20.4，那里这个签名已经不存在了。
     * 运行时在 1.20/1.20.1 上它就是真正的 override；在 1.20.2+ 上只是一个没人调的普通方法。
     *
     * <p>同理也<b>不能调 {@code super.mouseScrolled(三个参数)}</b>：1.20.4 的 {@code Element}
     * 只有 4 参版本，编译器会直接报「method mouseScrolled in class Screen cannot be applied to given types」。
     * 没人接手就返回 false，跟父类默认行为一致。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        return onScroll(mouseX, mouseY, 0, vertical);
    }

    /** 关掉自己并回父屏（UI 代码统一调这个，不碰版本钩子名）。 */
    public void closeSelf() {
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    @Override
    public void close() {
        closeSelf();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 键盘：UI 层只 override 这个；版本差异关在 compat 里。 */
    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return keyPressedDefault(keyCode, scanCode, modifiers);
    }
}
