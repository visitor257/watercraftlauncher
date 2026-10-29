package dev.mcver.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 版本/loader 差异层（mojmap 版，对准 MC 1.21.1 / NeoForge 21.1）。
 *
 * <p>和 Fabric 版的区别只在命名：
 * <ul>
 *   <li>{@code Screen#render(GuiGraphics,int,int,float)}（yarn: {@code DrawContext}）；</li>
 *   <li>{@code Screen#close()} → {@code onClose()}；{@code shouldPause()} → {@code isPauseScreen()}；</li>
 *   <li>{@code addDrawableChild} → {@code addRenderableWidget}；{@code clearChildren} → {@code clearWidgets()}；</li>
 *   <li>1.21.1 的 {@code Screen#render} 自己会调 {@code renderBackground}，所以面板在那边画。</li>
 * </ul>
 *
 * <p>页面代码只见 {@link Ctx}，不出现 {@code GuiGraphics}；钩子签名一变只改本文件。
 */
public abstract class ChromeScreen extends Screen {

    /** 关界面要回哪个屏；子类构造时设。 */
    protected Screen parentScreen;

    protected ChromeScreen(Component title) {
        super(title);
    }

    /** 面板底、标题栏、导航分隔线，由子类画。 */
    protected abstract void drawChrome(Ctx ctx);

    /** 页内容。父类已经画完背景和控件，这里只补自己的东西。 */
    protected abstract void renderContent(Ctx ctx, int mouseX, int mouseY, float delta);

    /** 滚轮：先给当前页；没人接就交回父类。 */
    protected abstract boolean onScroll(double mouseX, double mouseY, double horizontal, double vertical);

    private boolean boundsDumped;

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // 排查用：MCVER_DEBUG_BOUNDS=1 时把每个控件的实际矩形打一次进 mcver-debug.txt。
        // 为什么需要：Forge 1.17.1+ 上面板画得出来、控件既看不见也点不中，
        // 得先分清是「尺寸/坐标错了」还是「位置对但画不出来」。
        if (!boundsDumped && System.getenv("MCVER_DEBUG_BOUNDS") != null) {
            boundsDumped = true;
            StringBuilder sb = new StringBuilder("bounds:");
            for (Object o : this.children()) {
                if (o instanceof AbstractWidget) {
                    AbstractWidget aw = (AbstractWidget) o;
                    sb.append(" {").append(aw.getClass().getSimpleName())
                            .append(" @").append(aw.getX()).append(",").append(aw.getY())
                            .append(" ").append(aw.getWidth()).append("x").append(aw.getHeight())
                            .append(" vis=").append(aw.visible).append("}");
                } else {
                    sb.append(" {").append(o.getClass().getSimpleName()).append("}");
                }
            }
            try {
                dev.mcver.core.Jdk.writeAppend(java.nio.file.Paths.get("mcver-debug.txt").toAbsolutePath(),
                        sb.toString());
            } catch (Throwable ignored) { }
            // 再挖一层：Screen 内部的几个 List（children / renderables / narratables）各有多少条。
            // children 有、renderables 没有 = 控件只登记进了"能点"的列表，而渲染循环遍历的是另一个。
            StringBuilder ls = new StringBuilder("screenlists:");
            try {
                for (java.lang.reflect.Field f : net.minecraft.client.gui.screens.Screen.class.getDeclaredFields()) {
                    if (java.util.List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object v = f.get(this);
                        ls.append(" ").append(f.getName())
                          .append("(").append(f.getGenericType().getTypeName()).append(")=")
                          .append(((java.util.List<?>) v).size());
                    }
                }
            } catch (Throwable t) {
                ls.append(" ERR ").append(t);
            }
            try {
                dev.mcver.core.Jdk.writeAppend(java.nio.file.Paths.get("mcver-debug.txt").toAbsolutePath(),
                        ls.toString());
            } catch (Throwable ignored) { }
        }
        // 1.20.1 的 Screen#render **不会**自己调 renderBackground（1.20.2+ 才会），
        // 所以面板必须在这里手动先画；否则整块背景板是透明的（Fabric 1.20.1 踩过同一个坑）。
        // MCVER_DEBUG_NOCHROME=1 时跳过面板和页内容，只让原版控件画 —— 用来分辨
        // 「控件根本没被画」和「控件画了但被我们自己的面板盖住」。
        boolean noChrome = System.getenv("MCVER_DEBUG_NOCHROME") != null;
        boolean noContent = System.getenv("MCVER_DEBUG_NOCONTENT") != null;
        if (!noChrome) renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        if (!noChrome && !noContent) renderContent(Ctx.of(ctx), mouseX, mouseY, delta);
        // 排查用：原版 super.render 没画出控件时，自己遍历 children 再画一遍。
        // 如果这样能看见控件，说明控件本身画得出来，问题在"没进渲染列表"。
        if (System.getenv("MCVER_DEBUG_MANUALRENDER") != null) {
            for (Object o : this.children()) {
                if (o instanceof net.minecraft.client.gui.components.Renderable) {
                    ((net.minecraft.client.gui.components.Renderable) o).render(ctx, mouseX, mouseY, delta);
                }
            }
        }
        // 排查用：数一帧里 renderBackground 被调了几次。
        // 1 次/帧 = 正常（我们自己调）；2 次/帧 = 原版 Screen#render 也在调 →
        // 面板会在控件之后又画一遍，把控件盖掉（这正是 Forge 1.17.1+ 看起来"控件消失"的形态）。
        frames++;
        if (!bgLogged && frames == 120 && System.getenv("MCVER_DEBUG_BOUNDS") != null) {
            bgLogged = true;
            try {
                dev.mcver.core.Jdk.writeAppend(java.nio.file.Paths.get("mcver-debug.txt").toAbsolutePath(),
                        "framecount: frames=" + frames + " bgCalls=" + bgCalls);
            } catch (Throwable ignored) { }
        }
    }

    @Override
    public void renderBackground(GuiGraphics ctx) {
        // 1.20.1 只有单参 renderBackground（1.20.2 才加 x/y/delta）
        bgCalls++;
        super.renderBackground(ctx);
        drawChrome(Ctx.of(ctx));
    }

    private int frames;
    private int bgCalls;
    private boolean bgLogged;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        // 1.20.1 没有 horizontal 参数（1.20.2 才加）
        if (onScroll(mouseX, mouseY, 0.0, vertical)) return true;
        return super.mouseScrolled(mouseX, mouseY, vertical);
    }

    /** 加一个控件。别名：{@code addRenderableWidget}。
     *
     * <p><b>为什么叫 {@code addControl} 而不是 {@code addWidget}（别再改回去）</b>：
     * 1.17.1+ 的原版 {@code Screen} 自己就有
     * {@code <T extends GuiEventListener & NarratableEntry> T addWidget(T)}，它<b>只把控件放进
     * children + narratables，不进 renderables</b> —— 结果是「能点、但永远不画」。
     * 我们原来那个 {@code addWidget(Object)} 参数比它更宽，javac 在调用点会选原版那个。
     * 2026-10-29 就是这样：14 个 Forge/NeoForge target 的面板里一个控件都看不见
     * （反射看 Screen 内部三个 List 才确认：children=17 / narratables=17 / renderables=0）。
     *
     * <p>参数故意收 {@code Object}：主源码里 {@code MainScreen.add(Object)} / {@code Page.add} 是
     * 按 Fabric 那套（yarn 的 {@code addDrawableChild} 收 ClickableWidget 子类）写的，
     * 1.20.4 的 {@code Screen#addWidget} 泛型上限是 {@code GuiEventListener & NarratableEntry}，
     * 传 Object 编不过。在这里收窄成运行时判断，其他 target 行为不变。 */
    protected void addControl(Object w) {
        if (w instanceof AbstractWidget aw) {
            addRenderableWidget(aw);
        }
    }

    /** 泛型版：返回原对象，方便链式写法。 */
    protected <T extends AbstractWidget> T add(T w) {
        addControl(w);
        return w;
    }

    /** 清空控件。 */
    protected void clearWidgets() {
        clearWidgets0();
    }

    private void clearWidgets0() {
        super.clearWidgets();
    }

    /**
     * 键盘：子类实现这个。1.21.1 的签名还是 {@code keyPressed(int,int,int)}，
     * 所以这里可以直接接住并转给子类。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyPressedDefault(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected boolean keyPressedDefault(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** 关掉自己并回父屏。业务代码统一调这个。 */
    public void closeSelf() {
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void onClose() {
        closeSelf();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
