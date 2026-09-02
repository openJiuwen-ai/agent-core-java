/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;

/**
 * Filter for logging callback execution.
 * 
 * @since 0.1.7
 */
public class LoggingFilter extends EventFilter {
    private final Logger log;

    /**
     * LoggingFilter.
     * 
     * @since 0.1.7
     */
    public LoggingFilter() {
        this(null, "Logging");
    }

    /**
     * LoggingFilter.
     * 
     * @param logger logger
     * @param name name
     * @since 0.1.7
     */
    public LoggingFilter(Logger logger, String name) {
        super(name);
        this.log = logger != null ? logger : LoggerFactory.getLogger(LoggingFilter.class);
    }

    /**
     * filter.
     * 
     * @param event event
     * @param callback callback
     * @param args args
     * @param kwargs kwargs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public FilterResult filter(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs) {
        log.info("Event: {}, Callback: {}, Args: {}, Kwargs: {}", event, callback.getCallbackDisplayName(),
                Arrays.toString(args), kwargs);
        return FilterResult.continueResult();
    }
}
