package dev.mcver.core;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * 1.7.2 的「主线程调度 / 剪贴板」适配。
 *
 * <p><b>为什么不能照抄 1.7.10</b>：1.7.10 有 {@code MinecraftClient#execute(Callable)}，
 * 1.7.2 的 {@code MinecraftClient} 里<b>一个收 Runnable/Callable 的方法都没有</b>
 * （javap 全表核对过：只有 {@code scheduleStop} 之类）。所以这两代没法共用一份 {@code exec}。
 *
 * <p>办法：自己做一个队列，任务由 {@link #drain()} 在<b>客户端线程</b>上执行；
 * {@code drain()} 挂在 {@code ChromeScreen#tick()}（{@code Screen#tick} 由 MC 每 tick 调，
 * 1.7 也有）。因此任务最多延后一 tick（50ms），且只在界面临时打开期间执行 ——
 * 对「下载完成刷新列表」「登录回调改状态」这类 UI 更新完全够用。
 *
 * <p>同一 tick 内的语义差别：{@code exec} 在主线程上调用时会<b>立刻</b>跑（不排队），
 * 保持和 {@code mc.execute} 一致的顺序直觉。
 */
public final class Ui {

    private Ui() { }

    private static final Queue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile Thread clientThread;

    public static void exec(MinecraftClient mc, Runnable r) {
        if (r == null) return;
        Thread t = Thread.currentThread();
        if (clientThread == null) clientThread = t;
        if (t == clientThread) {
            // 已经在主线程上：直接跑，别排队（否则「设状态 → 立刻读」会读不到）
            r.run();
            return;
        }
        QUEUE.add(r);
    }

    /** 在客户端线程上把排队的任务跑完；由 {@code ChromeScreen#tick()} 调用。 */
    public static void drain() {
        clientThread = Thread.currentThread();
        Runnable r;
        int guard = 0;
        while ((r = QUEUE.poll()) != null) {
            try {
                r.run();
            } catch (Throwable e) {
                System.err.println("[mcver] 排队的界面任务出错: " + e);
            }
            // 任务里可能又 exec 一串（重建界面等）：留个上限，别卡住整帧
            if (++guard > 200) break;
        }
    }

    /** 1.7.2 的 {@code Screen#setClipboard} 没被 yarn 命名，是 {@code method_21939}。 */
    public static void clipboard(String s) {
        try {
            Screen.method_21939(s == null ? "" : s);
        } catch (Throwable ignored) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(s == null ? "" : s), null);
            } catch (Throwable ignored2) {
                // 剪贴板不是关键路径
            }
        }
    }
}
