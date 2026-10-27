package dev.mcver.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志适配层（mojmap 版）。
 *
 * <p>Fabric 1.16 那代没有 slf4j（只有 log4j2），所以 Fabric 版给 1.16 单独留了一份
 * compat 实现。NeoForge/Forge 这边 1.17+ 一律有 slf4j 桥（NeoForge 21.1 直接带
 * {@code log4j-slf4j2-impl}），1.16.5 Forge 的 compat 版再退回 log4j。
 */
public final class Log {

    private final Logger inner;

    public Log(String name) {
        this.inner = LoggerFactory.getLogger(name);
    }

    public void info(String fmt, Object... args) {
        inner.info(fmt, args);
    }

    public void warn(String fmt, Object... args) {
        inner.warn(fmt, args);
    }

    public void error(String fmt, Object... args) {
        inner.error(fmt, args);
    }

    public void debug(String fmt, Object... args) {
        inner.debug(fmt, args);
    }
}
