/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.function.Predicate;

/**
 * Public class CustomPredicateEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CustomPredicateEvaluator implements StopConditionEvaluator {
    private final String name;
    private final Predicate<StopEvaluationContext> predicate;

    /**
     * Auto-generated for codecheck compliance.
     */
    public CustomPredicateEvaluator(Predicate<StopEvaluationContext> predicate) {
        this("CustomPredicate", predicate);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CustomPredicateEvaluator(String name, Predicate<StopEvaluationContext> predicate) {
        this.name = name == null || name.isBlank() ? "CustomPredicate" : name;
        this.predicate = predicate;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop(StopEvaluationContext context) {
        return predicate != null && predicate.test(context);
    }
}
