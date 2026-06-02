/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.function.Predicate;

/**
 * Stop based on a user-supplied predicate.
 *
 * <p>Mirrors Python's {@code CustomPredicateEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public class CustomPredicateEvaluator implements StopConditionEvaluator {

    private final Predicate<StopEvaluationContext> predicate;

    public CustomPredicateEvaluator(Predicate<StopEvaluationContext> predicate) {
        this.predicate = predicate;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return predicate != null && predicate.test(ctx);
    }
}
