package dev.mcver.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * 1.19.3 ~ 1.19.4 渲染上下文适配层（{@code src/main} 那份是给 1.20+ 的 DrawContext 用的）。
 *
 * <p>1.19 时代还没有 {@code DrawContext}（1.20 才引入），绘制要自己拿 {@link MatrixStack}。
 *
 * <p><b>为什么 {@code drawText} / {@code drawItem} 要用 MethodHandle 而不是直接调</b>：
 * 这一个 jar 要同时盖住 1.19.3 和 1.19.4，但这两版之间有两处签名变了：
 * <pre>
 *                        1.19.3                                   1.19.4
 *   TextRenderer#draw    (...,VertexConsumerProvider,boolean,int,int) (...,VertexConsumerProvider,TextLayerType,int,int)
 *   ItemRenderer#renderInGui (ItemStack,int,int)                     (MatrixStack,ItemStack,int,int)
 * </pre>
 * 一个 class 文件没法同时引用两种返回/参数类型，所以改成运行期按**参数类型/个数**找方法。
 * 不写死方法名 —— mod 在 jar 里被 remap 成 intermediary（{@code method_xxxxx}），
 * 只有「按描述符结构找」这一条路在 remap 环境里可靠。
 */
public final class Ctx {

    private static final MethodHandle DRAW_TEXT = findDrawText();
    private static final MethodHandle DRAW_ITEM = findDrawItem();

    private final MatrixStack matrices;

    private Ctx(MatrixStack matrices) {
        this.matrices = matrices;
    }

    public static Ctx of(MatrixStack matrices) {
        return new Ctx(matrices);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }

    /**
     * 找 {@code draw(Text, float, float, int, boolean, Matrix4f, VertexConsumerProvider, ?, int, int)}。
     * 第 8 个参数在 1.19.3 是 {@code boolean}、1.19.4 是 {@code TextLayerType} 枚举 —— 不关心，
     * 按「第 9/10 是 int、第 1 是 Text」认出它，再按实际类型填值（枚举取第一个常量 = NORMAL）。
     */
    private static MethodHandle findDrawText() {
        MethodHandles.Lookup lk = MethodHandles.lookup();
        for (java.lang.reflect.Method m : TextRenderer.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 10) continue;
            if (!p[0].isAssignableFrom(Text.class) && p[0] != Text.class) continue;
            if (p[4] != boolean.class || p[8] != int.class || p[9] != int.class) continue;
            // 第 6 个参数是矩阵（org.joml.Matrix4f）
            if (!p[5].getName().endsWith("Matrix4f")) continue;
            try {
                m.setAccessible(true);
                return lk.unreflect(m).asType(MethodType.methodType(
                        int.class, TextRenderer.class, Text.class, float.class, float.class,
                        int.class, boolean.class, p[5], p[6], p[7], int.class, int.class));
            } catch (Throwable ignored) {
                // 换下一个重载
            }
        }
        throw new IllegalStateException("mcver: 找不到 TextRenderer#draw(Text,...,10 参)");
    }

    /** 找 {@code renderInGui([MatrixStack,] ItemStack, int, int)}（1.19.4 多一个 MatrixStack）。 */
    private static MethodHandle findDrawItem() {
        MethodHandles.Lookup lk = MethodHandles.lookup();
        Class<?> owner = MinecraftClient.getInstance().getItemRenderer().getClass();
        MethodType want = MethodType.methodType(void.class, owner, MatrixStack.class, ItemStack.class, int.class, int.class);
        for (java.lang.reflect.Method m : owner.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            boolean withMatrix = p.length == 4 && p[0] == MatrixStack.class
                    && p[1] == ItemStack.class && p[2] == int.class && p[3] == int.class;
            boolean plain = p.length == 3 && p[0] == ItemStack.class
                    && p[1] == int.class && p[2] == int.class;
            if (!withMatrix && !plain) continue;
            try {
                m.setAccessible(true);
                MethodHandle h = lk.unreflect(m);
                // 三参版本：在 index 1 插一个「丢掉」的 MatrixStack，两种签名就统一了
                return (withMatrix ? h : MethodHandles.dropArguments(h, 1, MatrixStack.class)).asType(want);
            } catch (Throwable ignored) {
                // 换下一个
            }
        }
        throw new IllegalStateException("mcver: 找不到 ItemRenderer#renderInGui");
    }

    public void drawText(TextRenderer tr, Text t, int x, int y, int color, boolean shadow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            // 第 8 个参数的实际类型：boolean 直接用 false；枚举取第一个常量（NORMAL）
            Object eighth = DRAW_TEXT.type().parameterType(8) == boolean.class
                    ? (Object) Boolean.FALSE
                    : DRAW_TEXT.type().parameterType(8).getEnumConstants()[0];
            DRAW_TEXT.invoke(tr, t, (float) x, (float) y, color, shadow,
                    matrices.peek().getPositionMatrix(),
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    eighth, Integer.valueOf(0), Integer.valueOf(0xF000F0));
        } catch (Throwable e) {
            throw new RuntimeException("mcver: 画文字失败", e);
        }
    }

    public void drawItem(ItemStack stack, int x, int y) {
        Object renderer = MinecraftClient.getInstance().getItemRenderer();
        try {
            DRAW_ITEM.invoke(renderer, matrices, stack, x, y);
        } catch (Throwable e) {
            throw new RuntimeException("mcver: 画物品失败", e);
        }
    }
}
