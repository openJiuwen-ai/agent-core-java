/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for {@link DialogueCompressor}.
 *
 * <p>Mirrors Python's {@code DialogueCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
 */
public class DialogueCompressorConfig {
    @JsonProperty("messages_threshold")
    private Integer messagesThreshold;

    @JsonProperty("tokens_threshold")
    private int tokensThreshold = 10000;

    @JsonProperty("messages_to_keep")
    private Integer messagesToKeep;

    @JsonProperty("keep_last_round")
    private boolean keepLastRound = true;

    @JsonProperty("compression_target_tokens")
    private int compressionTargetTokens = 1800;

    @JsonProperty("custom_compression_prompt")
    private String customCompressionPrompt;

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    public Integer getMessagesThreshold() {
        return messagesThreshold;
    }

    public void setMessagesThreshold(Integer messagesThreshold) {
        validateNullableGt(messagesThreshold, "messages_threshold");
        this.messagesThreshold = messagesThreshold;
    }

    public int getTokensThreshold() {
        return tokensThreshold;
    }

    public void setTokensThreshold(int tokensThreshold) {
        validateGt(tokensThreshold, "tokens_threshold");
        this.tokensThreshold = tokensThreshold;
    }

    public Integer getMessagesToKeep() {
        return messagesToKeep;
    }

    public void setMessagesToKeep(Integer messagesToKeep) {
        validateNullableGt(messagesToKeep, "messages_to_keep");
        this.messagesToKeep = messagesToKeep;
    }

    public boolean isKeepLastRound() {
        return keepLastRound;
    }

    public void setKeepLastRound(boolean keepLastRound) {
        this.keepLastRound = keepLastRound;
    }

    public int getCompressionTargetTokens() {
        return compressionTargetTokens;
    }

    public void setCompressionTargetTokens(int compressionTargetTokens) {
        validateGt(compressionTargetTokens, "compression_target_tokens");
        this.compressionTargetTokens = compressionTargetTokens;
    }

    public String getCustomCompressionPrompt() {
        return customCompressionPrompt;
    }

    public void setCustomCompressionPrompt(String customCompressionPrompt) {
        this.customCompressionPrompt = customCompressionPrompt;
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

    private static void validateNullableGt(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }

    private static void validateGt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }
}
