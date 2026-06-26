package io.github.connellite.microorm.util;

import java.lang.reflect.Method;
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

    /** Creates a {@link Logger} for the given class (SLF4J when on the classpath, otherwise JUL). */
    public static Logger getLogger(Class<?> hostClass) {
        if (USE_SLF4J) {
            return new Slf4jLogger(hostClass);
        }
        return new JdkLogger(hostClass);
    }

    private record JdkLogger(java.util.logging.Logger logger) implements Logger {

        private JdkLogger(Class<?> logger) {
            this(java.util.logging.Logger.getLogger(logger.getName()));
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

    private static final class Slf4jLogger implements Logger {

        private final Object logger;
        private final Method isDebugEnabled;
        private final Method isTraceEnabled;
        private final Method isInfoEnabled;
        private final Method isWarnEnabled;
        private final Method isErrorEnabled;
        private final Method debug;
        private final Method trace;
        private final Method info;
        private final Method warn;
        private final Method error;

        private Slf4jLogger(Class<?> hostClass) {
            try {
                Class<?> loggerClass = Class.forName("org.slf4j.Logger");
                Class<?> factoryClass = Class.forName("org.slf4j.LoggerFactory");
                logger = factoryClass.getMethod("getLogger", Class.class).invoke(null, hostClass);
                isDebugEnabled = loggerClass.getMethod("isDebugEnabled");
                isTraceEnabled = loggerClass.getMethod("isTraceEnabled");
                isInfoEnabled = loggerClass.getMethod("isInfoEnabled");
                isWarnEnabled = loggerClass.getMethod("isWarnEnabled");
                isErrorEnabled = loggerClass.getMethod("isErrorEnabled");
                debug = loggerClass.getMethod("debug", String.class);
                trace = loggerClass.getMethod("trace", String.class);
                info = loggerClass.getMethod("info", String.class);
                warn = loggerClass.getMethod("warn", String.class);
                error = loggerClass.getMethod("error", String.class, Throwable.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("SLF4J is present but LoggerFactory could not be used", e);
            }
        }

        @Override
        public boolean isDebugEnabled() {
            return invokeBoolean(isDebugEnabled);
        }

        @Override
        public void debug(Supplier<String> message) {
            if (isDebugEnabled()) {
                invokeVoid(debug, message.get());
            }
        }

        @Override
        public void trace(Supplier<String> message) {
            if (invokeBoolean(isTraceEnabled)) {
                invokeVoid(trace, message.get());
            }
        }

        @Override
        public void info(Supplier<String> message) {
            if (invokeBoolean(isInfoEnabled)) {
                invokeVoid(info, message.get());
            }
        }

        @Override
        public void warn(Supplier<String> message) {
            if (invokeBoolean(isWarnEnabled)) {
                invokeVoid(warn, message.get());
            }
        }

        @Override
        public void error(Supplier<String> message, Throwable throwable) {
            if (invokeBoolean(isErrorEnabled)) {
                invokeVoid(error, message.get(), throwable);
            }
        }

        private boolean invokeBoolean(Method method) {
            try {
                return (boolean) method.invoke(logger);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("SLF4J call failed: " + method.getName(), e);
            }
        }

        private void invokeVoid(Method method, Object... args) {
            try {
                method.invoke(logger, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("SLF4J call failed: " + method.getName(), e);
            }
        }
    }
}
