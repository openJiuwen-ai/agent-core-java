/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mirrors Python's {@code ValidationFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class ValidationFilter extends EventFilter {

    private final ArgumentsValidator validator;

    public ValidationFilter(ArgumentsValidator validator) {
        this(validator, "Validation");
    }

    public ValidationFilter(ArgumentsValidator validator, String name) {
        super(name);
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Override
    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        try {
            if (!validator.validate(safeArgs(args), safeKwargs(kwargs))) {
                return FilterResult.skipResult("Argument validation failed");
            }
        } catch (Exception error) {
            return FilterResult.skipResult("Validation error: " + error.getMessage());
        }
        return FilterResult.continueResult();
    }

    /**
     * Mirrors Python's validator callable shape for
     * {@code openjiuwen/core/runner/callback/filters.py}.
     */
    @FunctionalInterface
    public interface ArgumentsValidator {

        boolean validate(Object[] args, Map<String, Object> kwargs) throws Exception;
    }
}
