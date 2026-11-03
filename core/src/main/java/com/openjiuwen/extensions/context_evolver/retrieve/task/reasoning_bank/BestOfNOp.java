/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Selects the best trajectory from parallel MaTTS candidates.
 *
 * <p>Mirrors Python's {@code BestOfNOp} in
 * {@code openjiuwen/extensions/context_evolver/retrieve/task/reasoning_bank/matts.py}.</p>
 */
public class BestOfNOp extends BaseOp {

    private static final LoggerProtocol LOGGER = Loggers.CONTEXT_ENGINE;

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<Map<String, Object>> trajectories =
                MattsSupport.trajectoryMaps(context.get("parallel_trajectories"));
        if (trajectories.isEmpty()) {
            LOGGER.warning("No parallel trajectories found, skipping Best-of-N");
            return CompletableFuture.completedFuture(null);
        }

        LOGGER.info("Selecting best trajectory from %s candidates", trajectories.size());
        Object query = MattsSupport.requireContext(context, "query");
        MattsAsyncLlm llm;
        try {
            llm = MattsSupport.requireLlm(getLlm());
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }

        String evalPrompt = buildEvaluationPrompt(trajectories, String.valueOf(query));
        try {
            CompletableFuture<Void> result = new CompletableFuture<>();
            llm.asyncGenerate(evalPrompt, 0.0D)
                    .whenComplete((response, error) -> {
                        if (error != null) {
                            LOGGER.error("Error in Best-of-N selection: %s", error);
                            applyFallback(context, trajectories);
                        } else {
                            applySelection(context, trajectories, response);
                        }
                        result.complete(null);
                    });
            return result;
        } catch (RuntimeException exception) {
            LOGGER.error("Error in Best-of-N selection: %s", exception);
            applyFallback(context, trajectories);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static String buildEvaluationPrompt(List<Map<String, Object>> trajectories, String query) {
        List<String> descriptions = new ArrayList<>();
        for (Map<String, Object> trajectory : trajectories) {
            int index = ((Number) trajectory.getOrDefault("index", 0)).intValue();
            String answer = MattsSupport.stringValue(trajectory.get("answer"));
            Object success = trajectory.getOrDefault("success", "Unknown");
            int steps = MattsSupport.sizeOfSteps(trajectory.get("steps"));
            descriptions.add("""
                    Trajectory %s:
                    Answer: %s
                    Success: %s
                    Steps: %s
                    """.formatted(index + 1, answer, success, steps));
        }
        int lastIndex = trajectories.size() - 1;
        return """
                You are an expert in evaluating agent trajectories. You will be given the user query and %s candidate trajectories.
                Your job is to select the single best trajectory that most effectively and efficiently solves the task.

                Query: %s

                %s

                ## Evaluation Criteria:
                1. Progress Toward Goal: How well the trajectory advances toward completing the task
                2. Trajectory Efficiency: How efficiently progress is achieved given number of steps
                3. Error Severity: Assess fatal, significant, or minor errors
                4. Overall Quality: Logical flow, coherence, and closeness to goal

                Return ONLY the index (0-%s) of the best trajectory.""".formatted(
                trajectories.size(),
                query,
                String.join("\n", descriptions),
                lastIndex);
    }

    private static void applySelection(RuntimeContext context,
                                       List<Map<String, Object>> trajectories,
                                       String response) {
        int bestIndex = 0;
        String responseText = MattsSupport.stringValue(response);
        for (int i = 0; i < trajectories.size(); i++) {
            if (responseText.contains(String.valueOf(i))) {
                bestIndex = i;
                break;
            }
        }
        LOGGER.info("Selected trajectory %s as best", bestIndex);
        Map<String, Object> bestTrajectory = trajectories.get(bestIndex);
        context.set("answer", bestTrajectory.get("answer"));
        context.set("best_trajectory_index", bestIndex);
        context.set("best_trajectory", bestTrajectory);

        long successCount = trajectories.stream()
                .filter(trajectory -> MattsSupport.truthy(trajectory.getOrDefault("success", false)))
                .count();
        context.set("pass_at_k", trajectories.isEmpty() ? 0.0D : (double) successCount / trajectories.size());
    }

    private static void applyFallback(RuntimeContext context, List<Map<String, Object>> trajectories) {
        Map<String, Object> first = trajectories.get(0);
        context.set("answer", first.get("answer"));
        context.set("best_trajectory_index", 0);
        context.set("best_trajectory", first);
    }
}
