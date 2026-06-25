package io.github.connellite.microorm.util;

import java.util.function.Supplier;

/** Internal logging facade (SLF4J when present, otherwise {@link java.util.logging}). */
public interface Logger {

    boolean isDebugEnabled();

    void debug(Supplier<String> message);

    void trace(Supplier<String> message);

    void info(Supplier<String> message);

    void warn(Supplier<String> message);

    void error(Supplier<String> message, Throwable throwable);
}
