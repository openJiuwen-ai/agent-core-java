/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link MessageSummaryOffloader}.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloaderConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_summary_offloader.py}.</p>
 */
public class MessageSummaryOffloaderConfig {
    private static final Set<String> VALID_ROLES = Set.of("user", "assistant", "tool");

    @JsonProperty("large_message_threshold")
    private int largeMessageThreshold = 1000;

    @JsonProperty("offload_message_type")
    private List<String> offloadMessageType = List.of("tool");

    @JsonProperty("protected_tool_names")
    private List<String> protectedToolNames = List.of("reload_original_context_messages");

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("summary_max_tokens")
    private int summaryMaxTokens = 900;

    @JsonProperty("enable_precise_step")
    private boolean enablePreciseStep = false;

    @JsonProperty("step_summary_max_context_messages")
    private int stepSummaryMaxContextMessages = 8;

    @JsonProperty("content_max_chars_for_compression")
    private int contentMaxCharsForCompression = 200000;

    public int getLargeMessageThreshold() {
        return largeMessageThreshold;
    }

    public void setLargeMessageThreshold(int largeMessageThreshold) {
        validateGt(largeMessageThreshold, "large_message_threshold");
        this.largeMessageThreshold = largeMessageThreshold;
    }

    public List<String> getOffloadMessageType() {
        return new ArrayList<>(offloadMessageType);
    }

    public void setOffloadMessageType(List<String> offloadMessageType) {
        if (offloadMessageType == null) {
            throw new IllegalArgumentException("offload_message_type must not be null");
        }
        for (String role : offloadMessageType) {
            if (!VALID_ROLES.contains(role)) {
                throw new IllegalArgumentException("offload_message_type contains unsupported role: " + role);
            }
        }
        this.offloadMessageType = new ArrayList<>(offloadMessageType);
    }

    public List<String> getProtectedToolNames() {
        return new ArrayList<>(protectedToolNames);
    }

    public void setProtectedToolNames(List<String> protectedToolNames) {
        if (protectedToolNames == null) {
            throw new IllegalArgumentException("protected_tool_names must not be null");
        }
        this.protectedToolNames = new ArrayList<>(protectedToolNames);
    }

    public ModelRequestConfig getModel() {
        return model;
    }

    public void setModel(ModelRequestConfig model) {
        this.model = model;
    }

    public ModelClientConfig getModelClient() {
        return modelClient;
    }

    public void setModelClient(ModelClientConfig modelClient) {
        this.modelClient = modelClient;
    }

    public int getSummaryMaxTokens() {
        return summaryMaxTokens;
    }

    public void setSummaryMaxTokens(int summaryMaxTokens) {
        validateGt(summaryMaxTokens, "summary_max_tokens");
        this.summaryMaxTokens = summaryMaxTokens;
    }

    public boolean isEnablePreciseStep() {
        return enablePreciseStep;
    }

    public void setEnablePreciseStep(boolean enablePreciseStep) {
        this.enablePreciseStep = enablePreciseStep;
    }

    public int getStepSummaryMaxContextMessages() {
        return stepSummaryMaxContextMessages;
    }

    public void setStepSummaryMaxContextMessages(int stepSummaryMaxContextMessages) {
        validateGt(stepSummaryMaxContextMessages, "step_summary_max_context_messages");
        this.stepSummaryMaxContextMessages = stepSummaryMaxContextMessages;
    }

    public int getContentMaxCharsForCompression() {
        return contentMaxCharsForCompression;
    }

    public void setContentMaxCharsForCompression(int contentMaxCharsForCompression) {
        validateGt(contentMaxCharsForCompression, "content_max_chars_for_compression");
        this.contentMaxCharsForCompression = contentMaxCharsForCompression;
    }

    private static void validateGt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }
}
