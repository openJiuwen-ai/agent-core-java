/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging.defaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventClassRegistry;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Default logger implementation backed by SLF4J + Logback.
 * <p>
 * Implements {@link LoggerProtocol} providing:
 * <ul>
 *   <li>Console and file output (configured via Logback)</li>
 *   <li>Structured event logging via JSON serialization</li>
 *   <li>MDC-based context injection (trace_id, log_type)</li>
 *   <li>Control character sanitization</li>
 * </ul>
 */
public class DefaultLogger implements LoggerProtocol {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String logType;
    private Map<String, Object> config;
    private final Logger slf4jLogger;
    private final java.util.logging.Logger julLogger;
    private final List<Filter> filters = new CopyOnWriteArrayList<>();

    public DefaultLogger(String logType, Map<String, Object> config) {
        this.logType = logType;
        this.config = config != null ? Map.copyOf(config) : Map.of();
        this.slf4jLogger = LoggerFactory.getLogger(logType);
        this.julLogger = java.util.logging.Logger.getLogger(logType + ".jul");
        this.julLogger.setUseParentHandlers(false);
        this.julLogger.setFilter(record -> filters.stream().allMatch(filter -> filter.isLoggable(record)));
    }

    // ==================== LoggerProtocol Implementation ====================

    @Override
    public void debug(String msg, Object... args) {
        if (slf4jLogger.isDebugEnabled()) {
            setMdc();
            slf4jLogger.debug(sanitize(msg), args);
            publishToJul(Level.FINE, msg, null, args);
            clearMdc();
        }
    }

    @Override
    public void info(String msg, Object... args) {
        if (slf4jLogger.isInfoEnabled()) {
            setMdc();
            slf4jLogger.info(sanitize(msg), args);
            publishToJul(Level.INFO, msg, null, args);
            clearMdc();
        }
    }

    @Override
    public void warning(String msg, Object... args) {
        if (slf4jLogger.isWarnEnabled()) {
            setMdc();
            slf4jLogger.warn(sanitize(msg), args);
            publishToJul(Level.WARNING, msg, null, args);
            clearMdc();
        }
    }

    @Override
    public void error(String msg, Object... args) {
        if (slf4jLogger.isErrorEnabled()) {
            setMdc();
            slf4jLogger.error(sanitize(msg), args);
            publishToJul(Level.SEVERE, msg, null, args);
            clearMdc();
        }
    }

    @Override
    public void critical(String msg, Object... args) {
        // SLF4J has no CRITICAL level; use ERROR
        if (slf4jLogger.isErrorEnabled()) {
            setMdc();
            slf4jLogger.error("[CRITICAL] " + sanitize(msg), args);
            publishToJul(Level.SEVERE, "[CRITICAL] " + msg, null, args);
            clearMdc();
        }
    }

    @Override
    public void exception(String msg, Throwable t, Object... args) {
        setMdc();
        slf4jLogger.error(sanitize(msg), t);
        publishToJul(Level.SEVERE, msg, t, args);
        clearMdc();
    }

    @Override
    public void log(int level, String msg, Object... args) {
        // Map numeric levels to SLF4J methods
        if (level >= 40) {
            error(msg, args);
        } else if (level >= 30) {
            warning(msg, args);
        } else if (level >= 20) {
            info(msg, args);
        } else {
            debug(msg, args);
        }
    }

    @Override
    public void setLevel(int level) {
        julLogger.setLevel(toJulLevel(level));
        if (slf4jLogger instanceof ch.qos.logback.classic.Logger logbackLogger) {
            logbackLogger.setLevel(toLogbackLevel(level));
        }
    }

    @Override
    public void addHandler(Handler handler) {
        if (handler != null) {
            julLogger.addHandler(handler);
        }
    }

    @Override
    public void removeHandler(Handler handler) {
        if (handler != null) {
            julLogger.removeHandler(handler);
        }
    }

    @Override
    public void addFilter(Filter filter) {
        if (filter != null) {
            filters.add(filter);
        }
    }

    @Override
    public void removeFilter(Filter filter) {
        if (filter != null) {
            filters.remove(filter);
        }
    }

    @Override
    public java.util.logging.Logger logger() {
        return julLogger;
    }

    @Override
    public Map<String, Object> getConfig() {
        return config;
    }

    @Override
    public void reconfigure(Map<String, Object> newConfig) {
        this.config = newConfig != null ? Map.copyOf(newConfig) : Map.of();
    }

    // ==================== Structured Event Logging ====================

    /**
     * Log a structured event. Serializes the event to JSON and logs at the appropriate level.
     */
    public void logEvent(String msg, LogEventType eventType, BaseLogEvent event) {
        if (event == null && eventType == null) {
            info(msg);
            return;
        }

        BaseLogEvent eventObj = event;
        if (eventObj == null) {
            eventObj = EventClassRegistry.createEvent(eventType);
            eventObj.setMessage(sanitize(msg));
            String traceId = LoggingUtils.getSessionId();
            if (!"default_trace_id".equals(traceId)) {
                eventObj.setTraceId(traceId);
            }
            eventObj.setModuleId(logType);
            eventObj.setModuleName(logType);
        }

        String json;
        try {
            json = OBJECT_MAPPER.writeValueAsString(eventObj.toMap());
        } catch (Exception e) {
            json = eventObj.toMap().toString();
        }

        LogLevel logLevel = eventObj.getLogLevel();
        switch (logLevel) {
            case DEBUG -> debug(json);
            case WARNING -> warning(json);
            case ERROR, CRITICAL -> error(json);
            default -> info(json);
        }
    }

    // ==================== Internal ====================

    private void setMdc() {
        MDC.put("trace_id", LoggingUtils.getSessionId());
        MDC.put("log_type", logType);
    }

    private void clearMdc() {
        MDC.remove("trace_id");
        MDC.remove("log_type");
    }

    private void publishToJul(Level level, String msg, Throwable throwable, Object... args) {
        String formatted = sanitize(formatMessage(msg, args));
        LogRecord record = new LogRecord(level, formatted);
        record.setLoggerName(julLogger.getName());
        record.setThrown(throwable);
        julLogger.log(record);
    }

    private static Level toJulLevel(int level) {
        if (level >= 50) {
            return Level.SEVERE;
        }
        if (level >= 40) {
            return Level.WARNING;
        }
        if (level >= 20) {
            return Level.INFO;
        }
        return Level.FINE;
    }

    private static ch.qos.logback.classic.Level toLogbackLevel(int level) {
        if (level >= 50) {
            return ch.qos.logback.classic.Level.ERROR;
        }
        if (level >= 40) {
            return ch.qos.logback.classic.Level.WARN;
        }
        if (level >= 20) {
            return ch.qos.logback.classic.Level.INFO;
        }
        return ch.qos.logback.classic.Level.DEBUG;
    }

    private static String formatMessage(String msg, Object... args) {
        if (msg == null || args == null || args.length == 0) {
            return msg;
        }
        String formatted = msg;
        for (Object arg : args) {
            formatted = formatted.replaceFirst("\\{}", java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return formatted;
    }

    /**
     * Sanitize control characters in log messages.
     */
    private static String sanitize(String msg) {
        if (msg == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(msg.length());
        for (int i = 0; i < msg.length(); i++) {
            char c = msg.charAt(i);
            int code = c;
            if (code < 32 || code == 127) {
                sb.append(switch (c) {
                    case '\r' -> "\\r";
                    case '\n' -> "\\n";
                    case '\t' -> "\\t";
                    case '\b' -> "\\b";
                    case '\f' -> "\\f";
                    case '\0' -> "\\0";
                    default -> String.format("\\x%02x", code);
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
