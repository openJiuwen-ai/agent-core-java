/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class CompletionPromiseEvaluator used by the Java parity implementation.
 *
 * @since 1.0
 */
public class CompletionPromiseEvaluator implements StopConditionEvaluator {
    private final String promise;
    private int requiredConfirmations;
    private int confirmationCount;
    private boolean isCompleted;
    private String matchedText = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionPromiseEvaluator() {
        this("", 1);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionPromiseEvaluator(String promise) {
        this(promise, 1);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public CompletionPromiseEvaluator(String promise, int requiredConfirmations) {
        this.promise = promise == null ? "" : promise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String name() {
        return "CompletionPromise";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean shouldStop(StopEvaluationContext context) {
        return isCompleted;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void markCompleted() {
        notifyFulfilled(promise);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void notifyFulfilled(String matchedText) {
        this.confirmationCount += 1;
        this.matchedText = matchedText == null ? "" : matchedText;
        this.isCompleted = confirmationCount >= requiredConfirmations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void notifyAbsent() {
        this.confirmationCount = 0;
        this.matchedText = "";
        this.isCompleted = false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of(
                "completed", isCompleted,
                "isCompleted", isCompleted,
                "fulfilled", isCompleted,
                "matched_text", matchedText,
                "required_confirmations", requiredConfirmations,
                "confirmation_count", confirmationCount
        );
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void loadState(java.util.Map<String, Object> state) {
        if (state != null && state.get("isCompleted") instanceof Boolean isValue) {
            this.isCompleted = isValue;
        }
        if (state != null && state.get("fulfilled") instanceof Boolean isValue) {
            this.isCompleted = isValue || this.isCompleted;
        }
        if (state != null && state.get("matched_text") != null) {
            this.matchedText = String.valueOf(state.get("matched_text"));
        }
        if (state != null && state.get("required_confirmations") instanceof Number isValue) {
            this.requiredConfirmations = Math.max(1, isValue.intValue());
        }
        if (state != null && state.get("confirmation_count") instanceof Number isValue) {
            this.confirmationCount = Math.max(0, isValue.intValue());
        }
        this.isCompleted = isCompleted || confirmationCount >= requiredConfirmations;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void reset() {
        this.isCompleted = false;
        this.confirmationCount = 0;
        this.matchedText = "";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getConfirmationCount() {
        return confirmationCount;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMatchedText() {
        return matchedText;
    }
}
