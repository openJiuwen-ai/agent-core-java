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
 * Mirrors Python's {@code LoggerProtocol} in
 * {@code openjiuwen/core/common/logging/protocol.py}.
 *
 * <p>In Java we use an interface instead of a Protocol class.</p>
 */
public interface LoggerProtocol {

    void debug(String msg, Object... args);

    void info(String msg, Object... args);

    void warning(String msg, Object... args);

    default void warn(String msg, Object... args) {
        warning(msg, args);
    }

    void error(String msg, Object... args);

    void critical(String msg, Object... args);

    /** Log exception with stack trace. */
    void exception(String msg, Throwable t, Object... args);

    void log(int level, String msg, Object... args);

    void setLevel(int level);

    /** Add a log handler. */
    default void addHandler(Handler handler) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /** Remove a log handler. */
    default void removeHandler(Handler handler) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /** Add a log filter. */
    default void addFilter(Filter filter) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /** Remove a log filter. */
    default void removeFilter(Filter filter) {
        // Default no-op — override in implementations backed by java.util.logging.
    }

    /** Return the inner logger object. */
    default Logger logger() {
        return null;
    }

    /** Get logger configuration. */
    Map<String, Object> getConfig();

    /** Reconfigure logger with new config. */
    void reconfigure(Map<String, Object> config);
}
