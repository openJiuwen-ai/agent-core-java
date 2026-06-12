/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Parallel MaTTS scaling operation for generating multiple trajectories.
 *
 * <p>Mirrors Python's {@code ParallelScalingOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public class ParallelScalingOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    private final int k;
    private final double temperature;

    public ParallelScalingOp() {
        this(3, 0.9D);
    }

    public ParallelScalingOp(int k, double temperature) {
        super(Map.of("k", k, "temperature", temperature));
        this.k = k;
        this.temperature = temperature;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        LOGGER.info("Executing parallel scaling with k=%s", k);
        Object query = MattsSupport.requireContext(context, "query");
        Object userId = MattsSupport.requireContext(context, "user_id");
        Object llm = getLlm();
        MattsTemperatureAware temperatureAware =
                llm instanceof MattsTemperatureAware aware ? aware : null;
        Double originalTemperature = null;
        if (temperatureAware != null) {
            originalTemperature = temperatureAware.getTemperature();
            temperatureAware.setTemperature(temperature);
        }

        List<Map<String, Object>> trajectories = new ArrayList<>();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < k; i++) {
            final int index = i;
            chain = chain.thenCompose(ignored -> generateTrajectory(context, query, userId, trajectories, index));
        }

        final Double temperatureToRestore = originalTemperature;
        final MattsTemperatureAware restoreTarget = temperatureAware;
        return chain.whenComplete((ignored, error) -> {
            if (restoreTarget != null) {
                restoreTarget.setTemperature(temperatureToRestore);
            }
        }).thenRun(() -> {
            context.set("parallel_trajectories", trajectories);
            context.set("scaling_factor", k);
            LOGGER.info("Generated %s trajectories", trajectories.size());
        });
    }

    private CompletableFuture<Void> generateTrajectory(RuntimeContext sourceContext,
                                                       Object query,
                                                       Object userId,
                                                       List<Map<String, Object>> trajectories,
                                                       int index) {
        LOGGER.info("Generating trajectory %s/%s", index + 1, k);
        RuntimeContext trajectoryContext = new RuntimeContext();
        trajectoryContext.set("query", query);
        trajectoryContext.set("user_id", userId);
        if (sourceContext.toDict().containsKey("retrieved_memories")) {
            trajectoryContext.set("retrieved_memories", sourceContext.get("retrieved_memories"));
        }

        Object agentFlow = sourceContext.get("agent_flow");
        if (!(agentFlow instanceof MattsAgentFlow flow)) {
            LOGGER.warning("No agent_flow found in context, skipping trajectory generation");
            return CompletableFuture.completedFuture(null);
        }

        return flow.run(trajectoryContext).thenRun(() -> {
            Map<String, Object> trajectory = new LinkedHashMap<>();
            trajectory.put("index", index);
            trajectory.put("context", trajectoryContext);
            trajectory.put("answer", trajectoryContext.get("answer", ""));
            trajectory.put("steps", trajectoryContext.get("steps", List.of()));
            trajectory.put("success", trajectoryContext.get("success", false));
            trajectories.add(trajectory);
        });
    }
}
