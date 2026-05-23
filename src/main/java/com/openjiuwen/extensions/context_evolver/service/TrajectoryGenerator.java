/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Standalone trajectory generation utilities.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.service.trajectory_generator}.
 *
 * Provides MaTTS-mode trial runners that work with any ReActAgent without
 * requiring ContextEvolvingReActAgent or ContextEvolutionRail.
 *
 * <h2>Functions</h2>
 * <ul>
 *   <li>format_trajectory(messages) - Convert a message list into a clean trajectory string</li>
 *   <li>summarize_trajectories(memory_service, user_id, params) - Convert trajectories + feedback into memory</li>
 *   <li>run_trials(...) - Run MaTTS trials (parallel / sequential / combined) and summarize</li>
 * </ul>
 */
public final class TrajectoryGenerator {

    private static final Map<String, String> ALGO_TO_NAME = new HashMap<>();

    static {
        ALGO_TO_NAME.put("ACE", "ace");
        ALGO_TO_NAME.put("ReasoningBank", "rb");
        ALGO_TO_NAME.put("ReMe", "reme");
        ALGO_TO_NAME.put("RefCon", "reme");
        ALGO_TO_NAME.put("DivCon", "reme");
    }

    private TrajectoryGenerator() {
    }

    /**
     * Parameters for summarize_trajectories operation.
     */
    public static class SummarizeTrajectoriesInput {
        private String query;
        private Object trajectory; // String or List<String>
        private String mattsMode;
        private String groundTruth;
        private Object feedback;
        private List<Integer> scores;

        public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode) {
            this.query = query;
            this.trajectory = trajectory;
            this.mattsMode = mattsMode;
        }

        public String getQuery() { return query; }
        public Object getTrajectory() { return trajectory; }
        public String getMattssMode() { return mattsMode; }
        public String getGroundTruth() { return groundTruth; }
        public Object getFeedback() { return feedback; }
        public List<Integer> getScores() { return scores; }

        public SummarizeTrajectoriesInput withGroundTruth(String groundTruth) {
            this.groundTruth = groundTruth;
            return this;
        }

        public SummarizeTrajectoriesInput withFeedback(Object feedback) {
            this.feedback = feedback;
            return this;
        }

        public SummarizeTrajectoriesInput withScores(List<Integer> scores) {
            this.scores = scores;
            return this;
        }
    }

    /**
     * Convert a message list into a clean trajectory string.
     *
     * @param messages List of message dictionaries with 'role' and 'content' keys.
     * @return Formatted string representation of messages.
     */
    public static String formatTrajectory(List<Map<String, Object>> messages) {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> message : messages) {
            String role = (String) message.get("role");
            String content = (String) message.get("content");

            switch (role) {
                case "system":
                    output.append("SYSTEM:\n").append(content).append("\n");
                    break;
                case "assistant":
                    output.append("ASSISTANT:\n").append(content).append("\n");
                    break;
                case "user":
                    output.append("USER:\n").append(content).append("\n");
                    break;
                case "tool":
                    output.append("TOOL:\n").append(content).append("\n");
                    break;
                default:
                    output.append(role.toUpperCase()).append(":\n").append(content).append("\n");
            }
        }
        return output.toString().trim();
    }

    /**
     * Convert trajectories + feedback into memory via TaskMemoryService.
     *
     * @param memoryService TaskMemoryService instance.
     * @param userId User identifier.
     * @param input SummarizeTrajectoriesInput parameters.
     * @return CompletableFuture with summary result.
     */
    public static CompletableFuture<Map<String, Object>> summarizeTrajectories(
            TaskMemoryService memoryService,
            String userId,
            SummarizeTrajectoriesInput input) {

        // Determine algorithm name from mattsMode
        String algoName = ALGO_TO_NAME.getOrDefault(input.getMattssMode(), "default");

        // Format trajectory for summarization
        String trajectoryText;
        if (input.getTrajectory() instanceof String) {
            trajectoryText = (String) input.getTrajectory();
        } else if (input.getTrajectory() instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) input.getTrajectory();
            trajectoryText = formatTrajectory(messages);
        } else {
            trajectoryText = String.valueOf(input.getTrajectory());
        }

        // Call memory service for summarization
        return memoryService.summarize(
            userId,
            algoName,
            input.getQuery(),
            List.of(trajectoryText),
            null,
            input.getScores()
        );
    }

    /**
     * Run MaTTS trials (parallel / sequential / combined) and summarize.
     *
     * @param memoryService TaskMemoryService instance.
     * @param userId User identifier.
     * @param inputs List of SummarizeTrajectoriesInput for each trial.
     * @param mode Execution mode: "parallel", "sequential", or "combined".
     * @return CompletableFuture with aggregated summary results.
     */
    public static CompletableFuture<List<Map<String, Object>>> runTrials(
            TaskMemoryService memoryService,
            String userId,
            List<SummarizeTrajectoriesInput> inputs,
            String mode) {

        if ("parallel".equals(mode)) {
            // Run all trials in parallel
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (SummarizeTrajectoriesInput input : inputs) {
                futures.add(summarizeTrajectories(memoryService, userId, input));
            }
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<Map<String, Object>> results = new ArrayList<>();
                    for (CompletableFuture<Map<String, Object>> f : futures) {
                        results.add(f.join());
                    }
                    return results;
                });
        } else if ("sequential".equals(mode)) {
            // Run trials sequentially
            List<Map<String, Object>> results = new ArrayList<>();
            CompletableFuture<List<Map<String, Object>>> chain = CompletableFuture.completedFuture(results);
            for (SummarizeTrajectoriesInput input : inputs) {
                chain = chain.thenCompose(r ->
                    summarizeTrajectories(memoryService, userId, input)
                        .thenApply(r::add)
                        .thenApply(x -> r)
                );
            }
            return chain;
        } else {
            // Combined mode: process in batches
            return runCombinedTrials(memoryService, userId, inputs, 3);
        }
    }

    /**
     * Run trials in combined mode (batched).
     *
     * @param memoryService TaskMemoryService instance.
     * @param userId User identifier.
     * @param inputs List of inputs.
     * @param batchSize Batch size for parallel processing.
     * @return CompletableFuture with aggregated results.
     */
    private static CompletableFuture<List<Map<String, Object>>> runCombinedTrials(
            TaskMemoryService memoryService,
            String userId,
            List<SummarizeTrajectoriesInput> inputs,
            int batchSize) {

        List<Map<String, Object>> allResults = new ArrayList<>();
        List<List<SummarizeTrajectoriesInput>> batches = partition(inputs, batchSize);

        CompletableFuture<List<Map<String, Object>>> chain = CompletableFuture.completedFuture(allResults);
        for (List<SummarizeTrajectoriesInput> batch : batches) {
            chain = chain.thenCompose(results -> {
                List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
                for (SummarizeTrajectoriesInput input : batch) {
                    futures.add(summarizeTrajectories(memoryService, userId, input));
                }
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        for (CompletableFuture<Map<String, Object>> f : futures) {
                            results.add(f.join());
                        }
                        return results;
                    });
            });
        }
        return chain;
    }

    /**
     * Partition a list into batches.
     *
     * @param list List to partition.
     * @param size Batch size.
     * @return List of batches.
     */
    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            batches.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return batches;
    }
}