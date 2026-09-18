/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Mirrors Python's {@code LoggingFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class LoggingFilter extends EventFilter {

    private final Logger logger;

    public LoggingFilter() {
        this(null, "Logging");
    }

    public LoggingFilter(Logger logger) {
        this(logger, "Logging");
    }

    public LoggingFilter(Logger logger, String name) {
        super(name);
        this.logger = logger != null ? logger : Logger.getLogger(LoggingFilter.class.getName());
    }

    public Logger getLogger() {
        return logger;
    }

    @Override
    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        logger.info(
                "Event: " + event
                        + ", Callback: " + callbackName(callback)
                        + ", Args: " + Arrays.toString(safeArgs(args))
                        + ", Kwargs: " + safeKwargs(kwargs)
        );
        return FilterResult.continueResult();
    }
}
