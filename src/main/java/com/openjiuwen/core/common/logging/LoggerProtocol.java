/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.common.logging;

import java.util.Map;

/**
 * Logger protocol — every logger implementation must satisfy this contract.
 * <p>
 * Java equivalent of Python's {@code LoggerProtocol}.
 * In Java we use an interface instead of a Protocol class.
 */
public interface LoggerProtocol {

    void debug(String msg, Object... args);

    void info(String msg, Object... args);

    void warning(String msg, Object... args);

    void error(String msg, Object... args);

    void critical(String msg, Object... args);

    /** Log exception with stack trace. */
    void exception(String msg, Throwable t, Object... args);

    void log(int level, String msg, Object... args);

    void setLevel(int level);

    /** Get logger configuration. */
    Map<String, Object> getConfig();

    /** Reconfigure logger with new config. */
    void reconfigure(Map<String, Object> config);
}
