/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Filter for validating callback arguments.
 * 
 * @since 0.1.7
 */
public class ValidationFilter extends EventFilter {
    private final Predicate<Map<String, Object>> validator;

    /**
     * ValidationFilter.
     * 
     * @param validator validator
     * @since 0.1.7
     */
    public ValidationFilter(Predicate<Map<String, Object>> validator) {
        this(validator, "Validation");
    }

    /**
     * ValidationFilter.
     * 
     * @param validator validator
     * @param name name
     * @since 0.1.7
     */
    public ValidationFilter(Predicate<Map<String, Object>> validator, String name) {
        super(name);
        this.validator = validator;
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
        try {
            if (!validator.test(kwargs)) {
                return FilterResult.skipResult("Argument validation failed");
            }
        } catch (Exception e) {
            return FilterResult.skipResult("Validation error: " + e.getMessage());
        }
        return FilterResult.continueResult();
    }
}
