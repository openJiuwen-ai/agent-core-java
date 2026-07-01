/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging.defaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.LogLevels;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.common.logging.StructuredLoggerMixin;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.EventClassRegistry;
import com.openjiuwen.core.common.logging.events.LogEventType;
import com.openjiuwen.core.common.logging.events.LogLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Default logger implementation backed by provider-neutral SLF4J and a JUL mirror.
 *
 * <p>Mirrors Python's {@code DefaultLogger} in
 * {@code openjiuwen/core/common/logging/default/default_impl.py}.</p>
 * <p>
 * Implements {@link LoggerProtocol} providing:
 * <ul>
 *   <li>Console and file output through the active SLF4J provider</li>
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
    private volatile int thresholdLevel = LogLevels.INFO;

    public DefaultLogger(String logType, Map<String, Object> config) {
        this.logType = logType;
        this.config = config != null ? new LinkedHashMap<>(config) : Map.of();
        ensureLogDirectory(this.config);
        this.slf4jLogger = LoggerFactory.getLogger(logType);
        this.julLogger = java.util.logging.Logger.getLogger(logType + ".jul");
        this.julLogger.setUseParentHandlers(false);
        this.julLogger.setFilter(record -> filters.stream().allMatch(filter -> filter.isLoggable(record)));
        applyConfiguredLevel(this.config);
    }

    // ==================== LoggerProtocol Implementation ====================

    @Override
    public void debug(String msg, Object... args) {
        if (!isThresholdEnabled(LogLevels.DEBUG)) {
            return;
        }
        if (slf4jLogger.isDebugEnabled()) {
            setMdc();
            try {
                slf4jLogger.debug(sanitize(msg), args);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.FINE, msg, null, args);
    }

    @Override
    public void info(String msg, Object... args) {
        if (!isThresholdEnabled(LogLevels.INFO)) {
            return;
        }
        if (slf4jLogger.isInfoEnabled()) {
            setMdc();
            try {
                slf4jLogger.info(sanitize(msg), args);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.INFO, msg, null, args);
    }

    @Override
    public void warning(String msg, Object... args) {
        if (!isThresholdEnabled(LogLevels.WARNING)) {
            return;
        }
        if (slf4jLogger.isWarnEnabled()) {
            setMdc();
            try {
                slf4jLogger.warn(sanitize(msg), args);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.WARNING, msg, null, args);
    }

    @Override
    public void error(String msg, Object... args) {
        if (!isThresholdEnabled(LogLevels.ERROR)) {
            return;
        }
        if (slf4jLogger.isErrorEnabled()) {
            setMdc();
            try {
                slf4jLogger.error(sanitize(msg), args);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.SEVERE, msg, null, args);
    }

    @Override
    public void critical(String msg, Object... args) {
        if (!isThresholdEnabled(LogLevels.CRITICAL)) {
            return;
        }
        // SLF4J has no CRITICAL level; use ERROR
        if (slf4jLogger.isErrorEnabled()) {
            setMdc();
            try {
                slf4jLogger.error("[CRITICAL] " + sanitize(msg), args);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.SEVERE, "[CRITICAL] " + msg, null, args);
    }

    @Override
    public void exception(String msg, Throwable t, Object... args) {
        if (!isThresholdEnabled(LogLevels.ERROR)) {
            return;
        }
        if (slf4jLogger.isErrorEnabled()) {
            setMdc();
            try {
                slf4jLogger.error(sanitize(msg), t);
            } finally {
                clearMdc();
            }
        }
        publishToJul(Level.SEVERE, msg, t, args);
    }

    @Override
    public void log(int level, String msg, Object... args) {
        // Map numeric levels to SLF4J methods
        if (level >= LogLevels.CRITICAL) {
            critical(msg, args);
        } else if (level >= LogLevels.ERROR) {
            error(msg, args);
        } else if (level >= LogLevels.WARNING) {
            warning(msg, args);
        } else if (level >= LogLevels.INFO) {
            info(msg, args);
        } else {
            debug(msg, args);
        }
    }

    @Override
    public void setLevel(int level) {
        int normalizedLevel = LogLevels.normalizeLogLevel(level, LogLevels.INFO);
        thresholdLevel = normalizedLevel;
        julLogger.setLevel(toJulLevel(normalizedLevel));
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
        this.config = newConfig != null ? new LinkedHashMap<>(newConfig) : Map.of();
        ensureLogDirectory(this.config);
        applyConfiguredLevel(this.config);
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
        } else {
            eventObj.setMessage(sanitize(msg));
        }
        enrichEventContext(eventObj);

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
            case ERROR -> error(json);
            case CRITICAL -> critical(json);
            default -> info(json);
        }
    }

    // ==================== Internal ====================

    private void applyConfiguredLevel(Map<String, Object> currentConfig) {
        if (currentConfig == null || !currentConfig.containsKey("level")) {
            return;
        }
        setLevel(LogLevels.normalizeLogLevel(currentConfig.get("level"), LogLevels.INFO));
    }

    private boolean isThresholdEnabled(int level) {
        return LogLevels.normalizeLogLevel(level, LogLevels.INFO) >= thresholdLevel;
    }

    private void enrichEventContext(BaseLogEvent eventObj) {
        String traceId = LoggingUtils.getSessionId();
        if (!"default_trace_id".equals(traceId) && eventObj.getTraceId() == null) {
            eventObj.setTraceId(traceId);
        }
        if (eventObj.getModuleId() == null) {
            eventObj.setModuleId(logType);
        }
        if (eventObj.getModuleName() == null) {
            eventObj.setModuleName(logType);
        }

        Map<String, Object> metadata = eventObj.getMetadata() == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(eventObj.getMetadata());
        Map<String, Object> logContext = new LinkedHashMap<>();
        logContext.put("log_type", logType);
        logContext.put("trace_id", traceId);
        metadata.put("_log_context", logContext);
        eventObj.setMetadata(metadata);
    }

    private void setMdc() {
        MDC.put("trace_id", LoggingUtils.getSessionId());
        MDC.put("member_id", LoggingUtils.getMemberId());
        MDC.put("log_type", logType);
    }

    private void clearMdc() {
        MDC.remove("trace_id");
        MDC.remove("member_id");
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
            return Level.SEVERE;
        }
        if (level >= 30) {
            return Level.WARNING;
        }
        if (level >= 20) {
            return Level.INFO;
        }
        return Level.FINE;
    }

    private static String formatMessage(String msg, Object... args) {
        return StructuredLoggerMixin.autoFormatMessage(msg, args);
    }

    private static void ensureLogDirectory(Map<String, Object> config) {
        if (!usesFileOutput(config)) {
            return;
        }
        Object logFileValue = config.get("log_file");
        if (logFileValue == null || String.valueOf(logFileValue).isBlank()) {
            return;
        }
        Path logFile = Path.of(expandUser(String.valueOf(logFileValue))).toAbsolutePath();
        Path logDir = logFile.getParent();
        if (logDir == null) {
            return;
        }
        try {
            Files.createDirectories(logDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create log directory `" + logDir + "`", e);
        }
    }

    private static boolean usesFileOutput(Map<String, Object> config) {
        Object output = config.get("output");
        if (output instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if ("file".equals(String.valueOf(item))) {
                    return true;
                }
            }
            return false;
        }
        return output == null || "file".equals(String.valueOf(output));
    }

    private static String expandUser(String path) {
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        if (path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
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


