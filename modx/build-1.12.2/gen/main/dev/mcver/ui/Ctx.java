package dev.mcver.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * 渲染上下文适配层。
 *
 * <p>UI 代码只碰三个原语：{@code fill} / {@code drawText} / {@code drawItem}。这三个
 * 在 1.20 之前根本没有 {@code net.minecraft.client.gui.DrawContext} 这个类型
 * （1.19.4 及更早是 {@code MatrixStack} + {@code VertexConsumerProvider}），
 * 所以把所有绘制都收口到这一个类，页面代码就跟 MC 版本无关了。
 *
 * <p>每个 target 一份实现：
 * <ul>
 *   <li>1.20+（本文件）：薄包一层 {@link DrawContext}；</li>
 *   <li>1.19.4 及更早（{@code src/compat/<target>/java}）：包 MatrixStack。</li>
 * </ul>
 *
 * <p><b>为什么 drawText 要走 MethodHandle：</b>
 * 1.21 系列里 {@code DrawContext#drawText(TextRenderer, Text, int, int, int, boolean)}
 * 的<b>返回类型</b>从 {@code int} 改成了 {@code void}（intermediary 名都是
 * {@code method_51439}，但描述符分别是 {@code ...Z)I} 和 {@code ...Z)V}）。
 * JVM 按描述符解析方法，所以按其中一端编出来的 jar 在另一端会直接
 * {@code NoSuchMethodError} —— 实测在 1.21 上就是这句：
 * <pre>
 *   NoSuchMethodError: 'void net.minecraft.class_332.method_51439(...int, boolean)'
 * </pre>
 * 一个 class 文件无法同时引用两种返回类型，所以这里在<b>运行期</b>按参数类型把方法找出来，
 * 再用 {@link MethodHandle} 调。不写死方法名：mod 会被 remap 成 intermediary 名，
 * 字符串反射（{@code Class.forName("net.minecraft...")}）在这里一律不可用，但
 * {@code DrawContext.class} 是编译期引用（会被正确 remap 成 {@code class_332}），
 * 拿它的 {@code getDeclaredMethods()} 再按参数类型比对，就跟名字无关了。
 *
 * <p>找不到就退化成 {@code drawTextWithShadow}（绘制带阴影的文字）：三个版本里它都是
 * {@code (TextRenderer, Text, int, int, int)I}，描述符稳定。宁可文字多一层阴影，也不能崩。
 */
public final class Ctx {

    /** drawText 的 MethodHandle；null 表示当前版本没找到（退化用 drawTextWithShadow）。 */
    private static final MethodHandle DRAW_TEXT = findDrawText();

    private final DrawContext inner;

    private Ctx(DrawContext inner) {
        this.inner = inner;
    }

    public static Ctx of(DrawContext ctx) {
        return new Ctx(ctx);
    }

    private static MethodHandle findDrawText() {
        try {
            for (Method m : DrawContext.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 6
                        && p[0] == TextRenderer.class
                        && p[1] == Text.class
                        && p[2] == int.class && p[3] == int.class
                        && p[4] == int.class && p[5] == boolean.class) {
                    return MethodHandles.lookup().unreflect(m);
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        inner.fill(x1, y1, x2, y2, color);
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        if (DRAW_TEXT != null) {
            try {
                // 返回类型可能是 void 也可能是 int，统一当无返回值用
                DRAW_TEXT.invoke(inner, tr, t, x, y, color, shadow);
                return;
            } catch (Throwable ignored) {
                // 落到下面那个稳定签名
            }
        }
        inner.drawTextWithShadow(tr, t, x, y, color);
    }

    /** 物品图标（左上角那颗下载工具 / 自检用的绿块）。1.19.4 走 ItemRenderer#renderInGui。 */
    public void drawItem(ItemStack stack, int x, int y) {
        inner.drawItem(stack, x, y);
    }
}
