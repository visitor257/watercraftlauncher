package dev.mcver.core;

import java.util.concurrent.Callable;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * 1.12.2 的「主线程调度 / 剪贴板」适配。
 *
 * <p>为什么需要它：主源码按 1.14+ 写的是 {@code mc.execute(Runnable)} 与
 * {@code mc.keyboard.setClipboard(...)}，1.12 里前者只有 {@code execute(Callable<V>)}
 * （void lambda 编不过）、后者根本没有 {@code keyboard} 字段。
 * 构建脚本会在生成 1.12.2 的源码副本时把这两处机械改成本类的调用。
 */
public final class Ui {

    private Ui() { }

    public static void exec(MinecraftClient mc, Runnable r) {
        mc.execute((Callable<Void>) () -> {
            r.run();
            return null;
        });
    }

    public static void clipboard(String s) {
        Screen.setClipboard(s);
    }
}
