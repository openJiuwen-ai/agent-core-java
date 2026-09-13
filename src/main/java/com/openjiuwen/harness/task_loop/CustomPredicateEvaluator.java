/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.function.Predicate;

/**
 * Public class CustomPredicateEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class CustomPredicateEvaluator implements StopConditionEvaluator {
    private final String name;
    private final Predicate<StopEvaluationContext> predicate;

    /**
     * CustomPredicateEvaluator.
     * 
     * @param predicate predicate
     * @since 0.1.7
     */
    public CustomPredicateEvaluator(Predicate<StopEvaluationContext> predicate) {
        this("CustomPredicate", predicate);
    }

    /**
     * CustomPredicateEvaluator.
     * 
     * @param name name
     * @param predicate predicate
     * @since 0.1.7
     */
    public CustomPredicateEvaluator(String name, Predicate<StopEvaluationContext> predicate) {
        this.name = name == null || name.isBlank() ? "CustomPredicate" : name;
        this.predicate = predicate;
    }

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * shouldStop.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shouldStop(StopEvaluationContext context) {
        return predicate != null && predicate.test(context);
    }
}
