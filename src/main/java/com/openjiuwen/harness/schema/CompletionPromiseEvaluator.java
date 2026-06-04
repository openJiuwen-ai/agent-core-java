/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stop evaluator fulfilled by {@code TaskCompletionRail} after a promise tag
 * has been observed.
 *
 * <p>Mirrors Python's {@code CompletionPromiseEvaluator} in
 * {@code openjiuwen.harness.schema.stop_condition}.
 */
public class CompletionPromiseEvaluator implements StopConditionEvaluator {

    private final String promise;
    private int requiredConfirmations;
    private boolean fulfilled;
    private String matchedText = "";
    private int confirmationCount;

    public CompletionPromiseEvaluator(String promise) {
        this(promise, 1);
    }

    public CompletionPromiseEvaluator(String promise, int requiredConfirmations) {
        this.promise = promise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    public void notifyFulfilled(String matchedText) {
        confirmationCount++;
        fulfilled = confirmationCount >= requiredConfirmations;
        this.matchedText = matchedText != null ? matchedText : "";
    }

    public void notifyAbsent() {
        confirmationCount = 0;
        fulfilled = false;
        matchedText = "";
    }

    @Override
    public boolean shouldStop(StopEvaluationContext ctx) {
        return fulfilled;
    }

    @Override
    public void reset() {
        notifyAbsent();
    }

    @Override
    public Map<String, Object> getState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("fulfilled", fulfilled);
        state.put("matched_text", matchedText);
        state.put("required_confirmations", requiredConfirmations);
        state.put("confirmation_count", confirmationCount);
        return state;
    }

    @Override
    public void loadState(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        fulfilled = Boolean.parseBoolean(String.valueOf(data.getOrDefault("fulfilled", false)));
        matchedText = String.valueOf(data.getOrDefault("matched_text", ""));
        requiredConfirmations = Math.max(1, intValue(data.get("required_confirmations"), requiredConfirmations));
        confirmationCount = Math.max(0, intValue(data.get("confirmation_count"), 0));
        fulfilled = fulfilled || confirmationCount >= requiredConfirmations;
    }

    public String getPromise() {
        return promise;
    }

    public int getRequiredConfirmations() {
        return requiredConfirmations;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
