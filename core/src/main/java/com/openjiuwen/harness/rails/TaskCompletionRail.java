/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.schema.CompletionPromiseEvaluator;
import com.openjiuwen.harness.schema.MaxRoundsEvaluator;
import com.openjiuwen.harness.schema.StopConditionEvaluator;
import com.openjiuwen.harness.schema.TimeoutEvaluator;
import com.openjiuwen.harness.task_loop.LoopCoordinator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates completion promises emitted by the model.
 *
 * <p>Mirrors Python's {@code TaskCompletionRail} in
 * {@code openjiuwen/harness/rails/task_completion_rail.py}.</p>
 */
public class TaskCompletionRail extends DeepAgentRail {

    private static final Pattern PROMISE_TAG_PATTERN = Pattern.compile(
            "<promise>\\s*(.*?)\\s*</promise>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final String taskInstruction;
    private final String completionPromise;
    private final int requiredConfirmations;
    private final boolean allowPromiseDetails;
    private final Integer maxRounds;
    private final Double timeoutSeconds;
    private final List<StopConditionEvaluator> extraEvaluators;

    public TaskCompletionRail() {
        this("", "");
    }

    public TaskCompletionRail(String taskInstruction, String completionPromise) {
        this(taskInstruction, completionPromise, 1, false, null, null, List.of());
    }

    public TaskCompletionRail(
            String taskInstruction,
            String completionPromise,
            int requiredConfirmations,
            boolean allowPromiseDetails
    ) {
        this(taskInstruction, completionPromise, requiredConfirmations, allowPromiseDetails, null, null, List.of());
    }

    public TaskCompletionRail(
            String taskInstruction,
            String completionPromise,
            int requiredConfirmations,
            boolean allowPromiseDetails,
            Integer maxRounds,
            Double timeoutSeconds,
            List<StopConditionEvaluator> evaluators
    ) {
        setPriority(70);
        this.taskInstruction = taskInstruction == null ? "" : taskInstruction;
        this.completionPromise = completionPromise == null ? "" : completionPromise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.allowPromiseDetails = allowPromiseDetails;
        this.maxRounds = maxRounds;
        this.timeoutSeconds = timeoutSeconds;
        this.extraEvaluators = evaluators == null ? List.of() : new ArrayList<>(evaluators);
    }

    public List<StopConditionEvaluator> buildEvaluators() {
        List<StopConditionEvaluator> evaluators = new ArrayList<>();
        if (maxRounds != null) {
            evaluators.add(new MaxRoundsEvaluator(maxRounds));
        }
        if (timeoutSeconds != null) {
            evaluators.add(new TimeoutEvaluator(timeoutSeconds));
        }
        if (!completionPromise.isBlank()) {
            evaluators.add(new CompletionPromiseEvaluator(completionPromise, requiredConfirmations));
        }
        evaluators.addAll(extraEvaluators);
        return evaluators;
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (!taskInstruction.isBlank()) {
            ctx.put("task_instruction", taskInstruction);
        }
        if (!completionPromise.isBlank()) {
            ctx.put("completion_promise", completionPromise);
        }
    }

    @Override
    public void beforeTaskIteration(CallbackContext ctx) {
        if (taskInstruction.isBlank() || ctx == null) {
            return;
        }
        Object queryValue = ctx.get("query");
        if (queryValue == null || String.valueOf(queryValue).isBlank()) {
            return;
        }
        if (Boolean.TRUE.equals(ctx.get("is_follow_up"))) {
            return;
        }
        ctx.put("query", taskInstruction.replace("{query}", String.valueOf(queryValue)));
    }

    @Override
    public void afterTaskIteration(CallbackContext ctx) {
        if (ctx == null || completionPromise.isBlank()) {
            return;
        }
        String output = extractOutput(ctx);
        String block = extractPromiseBlock(output);
        if (block == null) {
            ctx.put("promise_matches", false);
            return;
        }
        String matched = normalize(block);
        String expected = normalize(completionPromise);
        if (!matched.equals(expected)) {
            if (!allowPromiseDetails || !promiseMatches(block, completionPromise)) {
                ctx.put("promise_matches", false);
                return;
            }
            matched = expected;
        }
        ctx.put("promise_matches", true);
        notifyEvaluator(ctx, matched);
    }

    public static String extractPromiseBlock(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PROMISE_TAG_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    public static boolean promiseMatches(String block, String expected) {
        if (block == null || block.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        String expectedNorm = normalize(expected);
        for (String line : block.split("\\R")) {
            String firstLine = line.trim();
            if (!firstLine.isEmpty()) {
                String firstNorm = normalize(firstLine);
                return firstNorm.equals(expectedNorm) || firstNorm.startsWith(expectedNorm + " ");
            }
        }
        String blockNorm = normalize(block);
        return blockNorm.equals(expectedNorm) || blockNorm.startsWith(expectedNorm + " ");
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String extractOutput(CallbackContext ctx) {
        Object result = ctx.get("result");
        if (result instanceof Map<?, ?> map && map.containsKey("output")) {
            Object output = map.get("output");
            return output == null ? "" : String.valueOf(output);
        }
        Object output = ctx.get("output");
        return output == null ? "" : String.valueOf(output);
    }

    private static void notifyEvaluator(CallbackContext ctx, String matched) {
        if (ctx.getAgent() == null) {
            return;
        }
        LoopCoordinator coordinator = ctx.getAgent().loopCoordinator();
        if (coordinator == null) {
            return;
        }
        CompletionPromiseEvaluator evaluator = coordinator.getCompletionPromiseEvaluator();
        if (evaluator != null) {
            evaluator.notifyFulfilled(matched);
        }
    }

    // --- Getters for DeepAgent ---

    public boolean hasCompletionPromise() {
        return completionPromise != null && !completionPromise.isBlank();
    }

    public String getCompletionPromise() {
        return completionPromise;
    }

    public int getRequiredConfirmations() {
        return requiredConfirmations;
    }

    public Integer getMaxRounds() {
        return maxRounds;
    }

    public Double getTimeout() {
        return timeoutSeconds;
    }

    public List<StopConditionEvaluator> getExtraEvaluators() {
        return extraEvaluators;
    }

    public String applyTaskInstruction(String query, boolean isFollowUp) {
        if (taskInstruction == null || taskInstruction.isBlank() || isFollowUp) {
            return query;
        }
        return taskInstruction.replace("{query}", query == null ? "" : query);
    }

    public String extractMatchingPromise(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        Object output = result.get("output");
        String text = output == null ? "" : String.valueOf(output);
        return extractPromiseBlock(text);
    }
}
