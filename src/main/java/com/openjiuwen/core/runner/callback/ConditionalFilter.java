  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.callback;

import java.util.Map;

/**
 * Conditional filter based on custom predicate.
 * <p>
 * Executes callback only if a condition is met.
 */
public class ConditionalFilter extends EventFilter {

    /**
     * Predicate function: (event, callback, args, kwargs) -> boolean
     */
    @FunctionalInterface
    public interface ConditionPredicate {
        boolean test(String event, CallbackInfo callback, Object[] args, Map<String, Object> kwargs);
    }

    private final ConditionPredicate condition;
    private final FilterAction actionOnFalse;

    public ConditionalFilter(ConditionPredicate condition) {
        this(condition, FilterAction.SKIP, "Conditional");
    }

    public ConditionalFilter(ConditionPredicate condition, FilterAction actionOnFalse, String name) {
        super(name);
        this.condition = condition;
        this.actionOnFalse = actionOnFalse;
    }

    @Override
    public FilterResult filter(String event, CallbackInfo callback,
                                Object[] args, Map<String, Object> kwargs) {
        try {
            if (condition.test(event, callback, args, kwargs)) {
                return FilterResult.continueResult();
            } else {
                return FilterResult.builder()
                        .action(actionOnFalse)
                        .reason("Condition not satisfied")
                        .build();
            }
        } catch (Exception e) {
            return FilterResult.skipResult("Condition evaluation failed: " + e.getMessage());
        }
    }
}
