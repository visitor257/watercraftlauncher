package net.minecraft.client;

import java.io.File;

/**
 * 探针桩：让 {@code dev.mcver.core} 能在没有 Minecraft 的环境里单独编译。
 *
 * <p>core 里只有 {@link dev.mcver.core.Config#dir()} 触碰 Minecraft 类，所以一个
 * 「永远返回 null 的单例 + 一个 runDirectory」就够了 —— getInstance() 返回 null 时
 * Config.dir() 会退到当前目录的 config/。
 */
public class MinecraftClient {

    /** 与真实字段同名同类型，Config.dir() 靠它取运行目录。 */
    public File runDirectory = new File(".");

    public static MinecraftClient getInstance() {
        return null;
    }
}
