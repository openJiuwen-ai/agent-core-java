/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reme;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Preprocess ReMe trajectories for summarization.
 * <p>
 * Mirrors Python's {@code TrajectoryPreprocessOp} in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reme/update.py}.
 * </p>
 */
public class TrajectoryPreprocessOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Object> trajectories = objectList(context.get("trajectories", List.of()));
        if (trajectories.isEmpty()) {
            LOGGER.warning("No trajectories to process");
            context.set("success_trajectories", List.of());
            context.set("failure_trajectories", List.of());
            return CompletableFuture.completedFuture(null);
        }

        List<Double> scores = numberList(context.get("score", List.of()));
        double threshold = numberValue(context.get("threshold", 1), 1.0d);
        List<Object> successTrajectories = new ArrayList<>();
        List<Object> failureTrajectories = new ArrayList<>();
        int count = Math.min(trajectories.size(), scores.size());
        for (int index = 0; index < count; index++) {
            if (scores.get(index) >= threshold) {
                successTrajectories.add(trajectories.get(index));
            } else {
                failureTrajectories.add(trajectories.get(index));
            }
        }
        context.set("success_trajectories", successTrajectories);
        context.set("failure_trajectories", failureTrajectories);
        context.set("all_trajectories", new ArrayList<>(trajectories));
        LOGGER.info(
                "Preprocessed %s trajectories: %s success, %s failure",
                trajectories.size(),
                successTrajectories.size(),
                failureTrajectories.size()
        );
        return CompletableFuture.completedFuture(null);
    }

    @SuppressWarnings("unchecked")
    static List<Object> objectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return (List<Object>) list;
    }

    static List<Double> numberList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            } else if (item != null) {
                result.add(Double.parseDouble(String.valueOf(item)));
            }
        }
        return result;
    }

    static double numberValue(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
