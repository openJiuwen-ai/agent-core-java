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
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.service.trajectory_generator} in
 * {@code openjiuwen/extensions/context_evolver/service/trajectory_generator.py}.</p>
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
     *
     * <p>Mirrors Python's {@code SummarizeTrajectoriesInput} in
     * {@code openjiuwen/extensions/context_evolver/service/trajectory_generator.py}.</p>
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
        public String getMattsMode() { return mattsMode; }
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
        List<String> transcript = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.get("role"));
            String content = stringValue(message.get("content"));

            switch (role) {
                case "assistant":
                    if (!content.isBlank()) {
                        transcript.add("THOUGHT: " + content);
                    }
                    appendToolCalls(transcript, message.get("tool_calls"));
                    break;
                case "user":
                    transcript.add("USER: " + cleanUserContent(content));
                    break;
                case "tool":
                    transcript.add("OBSERVATION: " + content);
                    break;
                default:
                    break;
            }
        }
        return String.join("\n", transcript);
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

        String mattsMode = input.getMattsMode() == null || input.getMattsMode().isBlank()
                ? "none"
                : input.getMattsMode();
        List<String> trajectories = normalizeTrajectories(input.getTrajectory());
        if ("sequential".equals(mattsMode) && !trajectories.isEmpty()) {
            trajectories = List.of(trajectories.get(trajectories.size() - 1));
        }

        return memoryService.summarize(
            userId,
            mattsMode,
            input.getQuery(),
            trajectories,
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

    private static List<String> normalizeTrajectories(Object trajectory) {
        if (trajectory == null) {
            return List.of();
        }
        if (trajectory instanceof String text) {
            return List.of(text);
        }
        if (trajectory instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            if (list.get(0) instanceof Map<?, ?>) {
                List<Map<String, Object>> messages = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> copy = new HashMap<>();
                        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
                        messages.add(copy);
                    }
                }
                return List.of(formatTrajectory(messages));
            }
            List<String> values = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    values.add(String.valueOf(item));
                }
            }
            return values;
        }
        return List.of(String.valueOf(trajectory));
    }

    private static String cleanUserContent(String content) {
        String result = content == null ? "" : content;
        if (result.startsWith("Task:")) {
            result = result.substring("Task:".length()).stripLeading();
        }
        String relatedExperience = "Some Related Experience to help you complete the task";
        int relatedIndex = result.indexOf(relatedExperience);
        if (relatedIndex >= 0) {
            result = result.substring(0, relatedIndex).trim();
        }
        String questionMarker = "Question: ";
        int questionIndex = result.lastIndexOf(questionMarker);
        if (questionIndex >= 0) {
            result = result.substring(questionIndex + questionMarker.length());
        }
        return result.trim();
    }

    private static void appendToolCalls(List<String> transcript, Object value) {
        if (!(value instanceof List<?> calls)) {
            return;
        }
        for (Object call : calls) {
            Object name = readValue(call, "name");
            Object arguments = readValue(call, "arguments");
            transcript.add("ACTION: " + stringValue(name) + "(" + stringValue(arguments) + ")");
        }
    }

    private static Object readValue(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            return map.get(key);
        }
        String methodName = "get" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            return value.getClass().getMethod(methodName).invoke(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
