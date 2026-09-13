/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Logger protocol — every logger implementation must satisfy this contract.
 * <p>
 * Java equivalent of Python's {@code LoggerProtocol}.
 * In Java we use an interface instead of a Protocol class.
 * 
 * @since 0.1.7
 */
public interface LoggerProtocol {
    /**
     * debug.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void debug(String msg, Object... args);

    /**
     * info.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void info(String msg, Object... args);

    /**
     * warning.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void warning(String msg, Object... args);

    /**
     * warn.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    default void warn(String msg, Object... args) {
        warning(msg, args);
    }

    /**
     * error.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void error(String msg, Object... args);

    /**
     * critical.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void critical(String msg, Object... args);

    /**
     * Log exception with stack trace.
     * 
     * @param msg msg
     * @param t t
     * @param args args
     * @since 0.1.7
     */
    void exception(String msg, Throwable t, Object... args);

    /**
     * log.
     * 
     * @param level level
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    void log(int level, String msg, Object... args);

    /**
     * setLevel.
     * 
     * @param level level
     * @since 0.1.7
     */
    void setLevel(int level);

    /**
     * Add a log handler.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    default void addHandler(Handler handler) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /**
     * Remove a log handler.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    default void removeHandler(Handler handler) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /**
     * Add a log filter.
     * 
     * @param filter filter
     * @since 0.1.7
     */
    default void addFilter(Filter filter) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /**
     * Remove a log filter.
     * 
     * @param filter filter
     * @since 0.1.7
     */
    default void removeFilter(Filter filter) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /**
     * Return the inner logger object.
     * 
     * @return the result
     * @since 0.1.7
     */
    default Logger logger() {
        return null;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    Map<String, Object> getConfig();

    /**
     * Reconfigure logger with new config.
     * 
     * @param config config
     * @since 0.1.7
     */
    void reconfigure(Map<String, Object> config);
}
