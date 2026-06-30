package io.github.connellite.microorm.util;

import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Creates {@link Logger} instances using SLF4J when present, otherwise {@code java.util.logging}.
 */
public final class LoggerFactory {

    static final boolean USE_SLF4J;

    static {
        boolean useSlf4j;
        try {
            Class.forName("org.slf4j.Logger");
            useSlf4j = true;
        } catch (Exception e) {
            useSlf4j = false;
        }
        USE_SLF4J = useSlf4j;
    }

    private LoggerFactory() {
    }

    /**
     * Creates a {@link Logger} for the given class (SLF4J when on the classpath, otherwise JUL).
     */
    public static Logger getLogger(Class<?> hostClass) {
        if (USE_SLF4J) {
            return new Slf4jLogger(hostClass);
        }
        return new JdkLogger(hostClass);
    }

    private record JdkLogger(java.util.logging.Logger logger) implements Logger {

        private JdkLogger(Class<?> logger) {
            this(java.util.logging.Logger.getLogger(logger.getCanonicalName()));
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isLoggable(Level.FINE);
        }

        @Override
        public void debug(Supplier<String> message) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, message.get());
            }
        }

        @Override
        public void trace(Supplier<String> message) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, message.get());
            }
        }

        @Override
        public void info(Supplier<String> message) {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, message.get());
            }
        }

        @Override
        public void warn(Supplier<String> message) {
            if (logger.isLoggable(Level.WARNING)) {
                logger.log(Level.WARNING, message.get());
            }
        }

        @Override
        public void error(Supplier<String> message, Throwable throwable) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, message.get(), throwable);
            }
        }
    }

    private record Slf4jLogger(org.slf4j.Logger logger) implements Logger {

        private Slf4jLogger(Class<?> logger) {
            this(org.slf4j.LoggerFactory.getLogger(logger));
        }

        @Override
        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        @Override
        public void debug(Supplier<String> message) {
            if (logger.isDebugEnabled()) {
                logger.debug(message.get());
            }
        }

        @Override
        public void trace(Supplier<String> message) {
            if (logger.isTraceEnabled()) {
                logger.trace(message.get());
            }
        }

        @Override
        public void info(Supplier<String> message) {
            if (logger.isInfoEnabled()) {
                logger.info(message.get());
            }
        }

        @Override
        public void warn(Supplier<String> message) {
            if (logger.isWarnEnabled()) {
                logger.warn(message.get());
            }
        }

        @Override
        public void error(Supplier<String> message, Throwable throwable) {
            if (logger.isErrorEnabled()) {
                logger.error(message.get(), throwable);
            }
        }
    }
}
