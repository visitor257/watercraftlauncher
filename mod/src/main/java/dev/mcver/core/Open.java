package dev.mcver.core;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 用系统默认程序打开文件/文件夹。
 *
 * <p>不用 MC 的 {@code Util.getOperatingSystem()}：那套重载跨版本不兼容
 * （1.20.1 只有 File / URI / URL / String，1.21 只有 Path），一升一降就得写两份代码。
 * 这里直接走系统命令，稳当且不挑版本。
 */
public final class Open {

    private Open() { }

    /** 打开文件夹（或文件）。返回是否成功发起。 */
    public static boolean path(Path p) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) {
                // 文件夹必须走 explorer，start 对目录也能用但引号一多就翻车
                pb = new ProcessBuilder("explorer.exe", p.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", p.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder("xdg-open", p.toAbsolutePath().toString());
            }
            pb.redirectErrorStream(true);
            pb.start();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
