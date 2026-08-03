/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import java.util.Map;

/**
 * Mirrors Python's {@code CompletionPromiseEvaluator} in
 * {@code openjiuwen/harness/schema/stop_condition.py}.
 */
public final class CompletionPromiseEvaluator implements StopConditionEvaluator {

    private final String promise;
    private boolean fulfilled;
    private String matchedText;
    private int requiredConfirmations;
    private int confirmationCount;

    public CompletionPromiseEvaluator(String promise) {
        this(promise, 1);
    }

    public CompletionPromiseEvaluator(String promise, int requiredConfirmations) {
        this.promise = promise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
        this.matchedText = "";
    }

    public String getPromise() {
        return promise;
    }

    public void notifyFulfilled(String matchedText) {
        confirmationCount += 1;
        fulfilled = confirmationCount >= requiredConfirmations;
        this.matchedText = matchedText == null ? "" : matchedText;
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
        fulfilled = false;
        matchedText = "";
        confirmationCount = 0;
    }

    @Override
    public Map<String, Object> getState() {
        return Map.of(
                "fulfilled", fulfilled,
                "matched_text", matchedText,
                "required_confirmations", requiredConfirmations,
                "confirmation_count", confirmationCount
        );
    }

    @Override
    public void loadState(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        fulfilled = Boolean.TRUE.equals(data.get("fulfilled"));
        matchedText = String.valueOf(data.getOrDefault("matched_text", ""));
        requiredConfirmations = Math.max(1, intValue(data.get("required_confirmations"), requiredConfirmations));
        confirmationCount = Math.max(0, intValue(data.get("confirmation_count"), 0));
        fulfilled = fulfilled || confirmationCount >= requiredConfirmations;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
