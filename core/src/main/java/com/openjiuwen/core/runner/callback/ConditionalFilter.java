/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Mirrors Python's {@code ConditionalFilter} in
 * {@code openjiuwen/core/runner/callback/filters.py}.
 */
public class ConditionalFilter extends EventFilter {

    private final ConditionPredicate condition;

    private final FilterAction actionOnFalse;

    public ConditionalFilter(ConditionPredicate condition) {
        this(condition, FilterAction.SKIP, "Conditional");
    }

    public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse) {
        this(condition, actionOnFalse, "Conditional");
    }

    public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse, String name) {
        super(name);
        this.condition = Objects.requireNonNull(condition, "condition");
        this.actionOnFalse = actionOnFalse != null ? actionOnFalse : FilterAction.SKIP;
    }

    @Override
    public FilterResult filter(
            String event,
            Function<Map<String, Object>, Object> callback,
            Object[] args,
            Map<String, Object> kwargs
    ) {
        try {
            if (condition.test(event, callback, safeArgs(args), safeKwargs(kwargs))) {
                return FilterResult.continueResult();
            }
            return FilterResult.builder()
                    .action(actionOnFalse)
                    .reason("Condition not satisfied")
                    .build();
        } catch (Exception error) {
            return FilterResult.skipResult("Condition evaluation failed: " + error.getMessage());
        }
    }

    /**
     * Mirrors Python's conditional predicate signature for
     * {@code openjiuwen/core/runner/callback/filters.py}.
     */
    @FunctionalInterface
    public interface ConditionPredicate {

        boolean test(
                String event,
                Function<Map<String, Object>, Object> callback,
                Object[] args,
                Map<String, Object> kwargs
        ) throws Exception;
    }
}
