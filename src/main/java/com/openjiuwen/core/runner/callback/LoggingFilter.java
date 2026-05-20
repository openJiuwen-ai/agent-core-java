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
 */
public class LoggingFilter extends EventFilter {

    private final Logger log;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoggingFilter() {
        this(null, "Logging");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public LoggingFilter(Logger logger, String name) {
        super(name);
        this.log = logger != null ? logger : LoggerFactory.getLogger(LoggingFilter.class);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public FilterResult filter(String event, CallbackInfo callback,
                                Object[] args, Map<String, Object> kwargs) {
        log.info("Event: {}, Callback: {}, Args: {}, Kwargs: {}",
                event, callback.getCallbackDisplayName(), Arrays.toString(args), kwargs);
        return FilterResult.continueResult();
    }
}
