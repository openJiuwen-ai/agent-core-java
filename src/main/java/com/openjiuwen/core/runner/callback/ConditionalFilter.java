/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;

/**
 * Conditional filter based on custom predicate.
 * <p>
 * Executes callback only if a condition is met.
 * 
 * @since 0.1.7
 */
public class ConditionalFilter extends EventFilter {
    /**
     * ConditionPredicate.
     * 
     * @since 0.1.7
     */
    @FunctionalInterface
    public interface ConditionPredicate {
        /**
         * test.
         * 
         * @param event event
         * @param callback callback
         * @param args args
         * @param kwargs kwargs
         * @return the result
         * @since 0.1.7
         */
        boolean test(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs);
    }

    private final ConditionPredicate condition;
    private final FilterAction actionOnFalse;

    /**
     * ConditionalFilter.
     * 
     * @param condition condition
     * @since 0.1.7
     */
    public ConditionalFilter(ConditionPredicate condition) {
        this(condition, FilterAction.SKIP, "Conditional");
    }

    /**
     * ConditionalFilter.
     * 
     * @param condition condition
     * @param actionOnFalse actionOnFalse
     * @param name name
     * @since 0.1.7
     */
    public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse, String name) {
        super(name);
        this.condition = condition;
        this.actionOnFalse = actionOnFalse;
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
            if (condition.test(event, callback, args, kwargs)) {
                return FilterResult.continueResult();
            } else {
                return FilterResult.builder().action(actionOnFalse).reason("Condition not satisfied").build();
            }
        } catch (Exception e) {
            return FilterResult.skipResult("Condition evaluation failed: " + e.getMessage());
        }
    }
}
