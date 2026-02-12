// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.common.logging;

import java.util.Map;

/**
 * Logger protocol defining methods all logger implementations must provide
 * 
 * <p>Supports both plain text and structured logging. Structured logging methods
 * accept an {@link LogEventType} or a pre-built {@link BaseLogEvent} to create
 * JSON-formatted log entries, matching Python's **kwargs support for event_type/event.
 * 
 * <p>Corresponds to Python: protocol.py::LoggerProtocol
 * 
 * @since 0.1.4
 */
public interface LoggerProtocol {
    
    // ==================== Plain text logging methods ====================
    
    /**
     * Log debug level message
     * 
     * @param msg the message
     * @param args optional arguments
     */
    void debug(String msg, Object... args);
    
    /**
     * Log info level message
     * 
     * @param msg the message
     * @param args optional arguments
     */
    void info(String msg, Object... args);
    
    /**
     * Log warning level message
     * 
     * @param msg the message
     * @param args optional arguments
     */
    void warning(String msg, Object... args);
    
    /**
     * Log error level message
     * 
     * @param msg the message
     * @param args optional arguments
     */
    void error(String msg, Object... args);
    
    /**
     * Log critical level message
     * 
     * @param msg the message
     * @param args optional arguments
     */
    void critical(String msg, Object... args);
    
    /**
     * Log exception with stack trace
     * 
     * @param msg the message
     * @param cause the exception
     */
    void exception(String msg, Throwable cause);
    
    /**
     * Generic log method
     * 
     * @param level the log level
     * @param msg the message
     * @param args optional arguments
     */
    void log(int level, String msg, Object... args);
    
    // ==================== Structured logging methods ====================
    // These correspond to Python's **kwargs support for event_type/event parameters.
    // Provided as default methods for backward compatibility.
    
    /**
     * Log debug level message with structured event type
     * 
     * <p>Corresponds to Python: debug(msg, event_type=..., **kwargs)
     * 
     * @param msg the message
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void debug(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        debug(msg);
    }
    
    /**
     * Log debug level message with pre-built structured event
     * 
     * <p>Corresponds to Python: debug(msg, event=...)
     * 
     * @param msg the message
     * @param event the pre-built log event
     */
    default void debug(String msg, BaseLogEvent event) {
        debug(msg);
    }
    
    /**
     * Log info level message with structured event type
     * 
     * @param msg the message
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void info(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        info(msg);
    }
    
    /**
     * Log info level message with pre-built structured event
     * 
     * @param msg the message
     * @param event the pre-built log event
     */
    default void info(String msg, BaseLogEvent event) {
        info(msg);
    }
    
    /**
     * Log warning level message with structured event type
     * 
     * @param msg the message
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void warning(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        warning(msg);
    }
    
    /**
     * Log warning level message with pre-built structured event
     * 
     * @param msg the message
     * @param event the pre-built log event
     */
    default void warning(String msg, BaseLogEvent event) {
        warning(msg);
    }
    
    /**
     * Log error level message with structured event type
     * 
     * @param msg the message
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void error(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        error(msg);
    }
    
    /**
     * Log error level message with pre-built structured event
     * 
     * @param msg the message
     * @param event the pre-built log event
     */
    default void error(String msg, BaseLogEvent event) {
        error(msg);
    }
    
    /**
     * Log critical level message with structured event type
     * 
     * @param msg the message
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void critical(String msg, LogEventType eventType, Map<String, Object> kwargs) {
        critical(msg);
    }
    
    /**
     * Log critical level message with pre-built structured event
     * 
     * @param msg the message
     * @param event the pre-built log event
     */
    default void critical(String msg, BaseLogEvent event) {
        critical(msg);
    }
    
    /**
     * Log exception with structured event type
     * 
     * @param msg the message
     * @param cause the exception
     * @param eventType the event type for structured logging
     * @param kwargs additional event fields
     */
    default void exception(String msg, Throwable cause, LogEventType eventType, Map<String, Object> kwargs) {
        exception(msg, cause);
    }
    
    /**
     * Log exception with pre-built structured event
     * 
     * @param msg the message
     * @param cause the exception
     * @param event the pre-built log event
     */
    default void exception(String msg, Throwable cause, BaseLogEvent event) {
        exception(msg, cause);
    }
    
    // ==================== Configuration methods ====================
    
    /**
     * Set log level
     * 
     * @param level the log level
     */
    void setLevel(int level);
    
    /**
     * Get logger config
     * 
     * @return the configuration map
     */
    Map<String, Object> getConfig();
    
    /**
     * Reconfigure logger
     * 
     * @param config the new configuration
     */
    void reconfigure(Map<String, Object> config);
    
    /**
     * Add log handler
     * 
     * <p>Corresponds to Python: add_handler(handler: logging.Handler)
     * In Java, handler is typically an Appender or similar object depending on the logging framework.
     * 
     * @param handler the log handler to add
     */
    void addHandler(Object handler);
    
    /**
     * Remove log handler
     * 
     * <p>Corresponds to Python: remove_handler(handler: logging.Handler)
     * 
     * @param handler the log handler to remove
     */
    void removeHandler(Object handler);
    
    /**
     * Add log filter
     * 
     * <p>Corresponds to Python: add_filter(filter)
     * 
     * @param filter the log filter to add
     */
    void addFilter(Object filter);
    
    /**
     * Remove log filter
     * 
     * <p>Corresponds to Python: remove_filter(filter)
     * 
     * @param filter the log filter to remove
     */
    void removeFilter(Object filter);
    
    /**
     * Get the underlying logger object
     * 
     * @return the logger object
     */
    Object getLogger();
}

