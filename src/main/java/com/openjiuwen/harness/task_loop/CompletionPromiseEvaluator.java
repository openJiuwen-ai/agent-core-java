/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

/**
 * Public class CompletionPromiseEvaluator used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class CompletionPromiseEvaluator implements StopConditionEvaluator {
    private final String promise;
    private int requiredConfirmations;
    private int confirmationCount;
    private boolean isCompleted;
    private String matchedText = "";

    /**
     * CompletionPromiseEvaluator.
     * 
     * @since 0.1.7
     */
    public CompletionPromiseEvaluator() {
        this("", 1);
    }

    /**
     * CompletionPromiseEvaluator.
     * 
     * @param promise promise
     * @since 0.1.7
     */
    public CompletionPromiseEvaluator(String promise) {
        this(promise, 1);
    }

    /**
     * CompletionPromiseEvaluator.
     * 
     * @param promise promise
     * @param requiredConfirmations requiredConfirmations
     * @since 0.1.7
     */
    public CompletionPromiseEvaluator(String promise, int requiredConfirmations) {
        this.promise = promise == null ? "" : promise;
        this.requiredConfirmations = Math.max(1, requiredConfirmations);
    }

    /**
     * name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String name() {
        return "CompletionPromise";
    }

    /**
     * shouldStop.
     * 
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean shouldStop(StopEvaluationContext context) {
        return isCompleted;
    }

    /**
     * markCompleted.
     * 
     * @since 0.1.7
     */
    public void markCompleted() {
        notifyFulfilled(promise);
    }

    /**
     * notifyFulfilled.
     * 
     * @param matchedText matchedText
     * @since 0.1.7
     */
    public void notifyFulfilled(String matchedText) {
        this.confirmationCount += 1;
        this.matchedText = matchedText == null ? "" : matchedText;
        this.isCompleted = confirmationCount >= requiredConfirmations;
    }

    /**
     * notifyAbsent.
     * 
     * @since 0.1.7
     */
    public void notifyAbsent() {
        this.confirmationCount = 0;
        this.matchedText = "";
        this.isCompleted = false;
    }

    /**
     * getState.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public java.util.Map<String, Object> getState() {
        return java.util.Map.of("completed", isCompleted, "isCompleted", isCompleted, "fulfilled", isCompleted,
                "matched_text", matchedText, "required_confirmations", requiredConfirmations, "confirmation_count",
                confirmationCount);
    }

    /**
     * loadState.
     * 
     * @param state state
     * @since 0.1.7
     */
    @Override
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
     * reset.
     * 
     * @since 0.1.7
     */
    @Override
    public void reset() {
        this.isCompleted = false;
        this.confirmationCount = 0;
        this.matchedText = "";
    }

    /**
     * getConfirmationCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getConfirmationCount() {
        return confirmationCount;
    }

    /**
     * getMatchedText.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMatchedText() {
        return matchedText;
    }
}
