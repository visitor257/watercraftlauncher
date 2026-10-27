package dev.mcver.core;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.Callable;

import net.minecraft.client.MinecraftClient;

/**
 * 1.13.2 的「主线程调度 / 剪贴板」适配。
 *
 * <p>1.13 的 {@code MinecraftClient#execute} 只有 {@code Callable<V>} 版本（void lambda 编不过），
 * 而 {@code keyboard} 字段压根没被 yarn 命名 —— 所以这两件事都得从页面代码里搬出来。
 * 构建脚本对 {@code src/main} 做机械改写，把它们换成这里的方法（compat 目录不参与改写）。
 */
public final class Ui {

    private Ui() { }

    public static void exec(MinecraftClient mc, Runnable r) {
        mc.execute((Callable<Void>) () -> {
            r.run();
            return null;
        });
    }

    /** 1.13 里 {@code keyboard.setClipboard} 不存在，直接走 AWT。 */
    public static void clipboard(String s) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(s == null ? "" : s), null);
        } catch (Throwable ignored) {
            // 无头/受限环境就算了，剪贴板不是关键路径
        }
    }
}
