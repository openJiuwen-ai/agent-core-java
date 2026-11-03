/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Mirrors Python's {@code CustomPredicateEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class CustomPredicateEvaluator implements StopConditionEvaluator {

    private final Predicate<StopEvaluationContext> predicate;

    public CustomPredicateEvaluator(Predicate<StopEvaluationContext> predicate) {
        this.predicate = Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return predicate.test(ctx);
    }
}
