// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Filter for validating callback arguments.
 */
public class ValidationFilter extends EventFilter {

    private final Predicate<Map<String, Object>> validator;

    public ValidationFilter(Predicate<Map<String, Object>> validator) {
        this(validator, "Validation");
    }

    public ValidationFilter(Predicate<Map<String, Object>> validator, String name) {
        super(name);
        this.validator = validator;
    }

    @Override
    public FilterResult filter(String event, CallbackInfo callback,
                                Object[] args, Map<String, Object> kwargs) {
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
