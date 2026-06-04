/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.task;

/**
 * Accumulated token usage for one model id within a single invoke.
 *
 * <p>Mirrors Python's {@code ModelUsageRecord} in
 * {@code openjiuwen.harness.schema.task}.</p>
 */
public class ModelUsageRecord {

    private final String modelId;
    private int inputTokens;
    private int outputTokens;

    public ModelUsageRecord(String modelId) {
        this(modelId, 0, 0);
    }

    public ModelUsageRecord(String modelId, int inputTokens, int outputTokens) {
        this.modelId = modelId;
        this.inputTokens = Math.max(0, inputTokens);
        this.outputTokens = Math.max(0, outputTokens);
    }

    public void add(int inputTokens, int outputTokens) {
        this.inputTokens += Math.max(0, inputTokens);
        this.outputTokens += Math.max(0, outputTokens);
    }

    public String getModelId() {
        return modelId;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    @Override
    public String toString() {
        return "model_id=" + modelId + " input=" + inputTokens + " output=" + outputTokens;
    }
}
