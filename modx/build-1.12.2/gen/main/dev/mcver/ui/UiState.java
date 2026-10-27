package dev.mcver.ui;

import dev.mcver.core.Config;
import dev.mcver.core.Dirs;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;

/** 向导和启动器共用的 UI 状态（选定的游戏目录）。 */
public final class UiState {

    private UiState() { }

    public static Path gameDir;

    public static Path dir() {
        if (gameDir == null) {
            gameDir = MinecraftClient.getInstance().runDirectory.toPath().toAbsolutePath().normalize();
        }
        return gameDir;
    }

    /** 只改内存里的当前目录（不落盘）。 */
    public static void setDir(Path p) {
        gameDir = p.toAbsolutePath().normalize();
    }

    /** 用户主动切换：改当前 + 登记（连切换前那个一起留）+ 落盘。 */
    public static void switchDir(Path p) {
        Path old = dir();
        Path n = p.toAbsolutePath().normalize();
        setDir(n);
        Config.lastDir = n.toString();
        Dirs.add(n, old);
    }
}
