/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import java.util.Locale;
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

    public TaskCompletionRail() {
        this("", "");
    }

    public TaskCompletionRail(String taskInstruction, String completionPromise) {
        setPriority(70);
        this.taskInstruction = taskInstruction == null ? "" : taskInstruction;
        this.completionPromise = completionPromise == null ? "" : completionPromise;
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
    public void afterTaskIteration(CallbackContext ctx) {
        String output = String.valueOf(ctx.getValues().getOrDefault("output", ""));
        String block = extractPromiseBlock(output);
        ctx.put("promise_matches", promiseMatches(block, completionPromise));
    }

    public static String extractPromiseBlock(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = PROMISE_TAG_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    public static boolean promiseMatches(String block, String expected) {
        return normalize(block).equals(normalize(expected));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
