package dev.mcver.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志适配层。
 *
 * <p>1.16.5 及更早的 MC 用的是 log4j，**类路径上根本没有 slf4j**（1.17 起才由 Fabric 引入），
 * 而本项目从 1.17 开始所有代码都是 {@code McVerMod.LOGGER.info(...)}。为了让 UI / core
 * 一行都不用改，这里把「打日志」收口成一个只有 4 个方法的小类型，各版本自己实现：
 *
 * <ul>
 *   <li>1.17+（本文件）：转给 slf4j；</li>
 *   <li>1.16.5 及更早（{@code src/compat/<target>/java}）：转给 log4j2。</li>
 * </ul>
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
