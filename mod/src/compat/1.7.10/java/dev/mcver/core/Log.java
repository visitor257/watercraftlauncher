package dev.mcver.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.16.5 及更早的日志适配层：MC 这个年代用 log4j2，类路径上没有 slf4j。
 *
 * <p>log4j2 的 {@code info(String, Object...)} 也支持 {@code {}} 占位符，
 * 所以调用点的写法和 1.17+ 完全一样，不用改。
 */
public final class Log {

    private final Logger inner;

    public Log(String name) {
        this.inner = LogManager.getLogger(name);
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
