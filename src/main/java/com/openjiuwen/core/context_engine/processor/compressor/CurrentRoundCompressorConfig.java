/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.compressor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Configuration for {@link CurrentRoundCompressor}.
 *
 * <p>Mirrors Python's {@code CurrentRoundCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
 */
public class CurrentRoundCompressorConfig {
    @JsonProperty("tokens_threshold")
    private int tokensThreshold = 100000;

    @JsonProperty("messages_to_keep")
    private int messagesToKeep = 3;

    private ModelRequestConfig model;

    @JsonProperty("model_client")
    private ModelClientConfig modelClient;

    @JsonProperty("min_selected_tokens_for_compression")
    private int minSelectedTokensForCompression = 20000;

    @JsonProperty("compression_target_tokens")
    private int compressionTargetTokens = 4000;

    @JsonProperty("summary_merge_target_tokens")
    private int summaryMergeTargetTokens = 4000;

    @JsonProperty("accumulated_summary_token_limit")
    private int accumulatedSummaryTokenLimit = 20000;

    @JsonProperty("summary_merge_min_blocks")
    private int summaryMergeMinBlocks = 3;

    @JsonProperty("prior_context_window_size")
    private int priorContextWindowSize = 10;

    @JsonProperty("custom_compression_prompt")
    private String customCompressionPrompt;

    public int getTokensThreshold() {
        return tokensThreshold;
    }

    public void setTokensThreshold(int tokensThreshold) {
        validateGt(tokensThreshold, "tokens_threshold");
        this.tokensThreshold = tokensThreshold;
    }

    public int getMessagesToKeep() {
        return messagesToKeep;
    }

    public void setMessagesToKeep(int messagesToKeep) {
        validateGt(messagesToKeep, "messages_to_keep");
        this.messagesToKeep = messagesToKeep;
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

    public int getMinSelectedTokensForCompression() {
        return minSelectedTokensForCompression;
    }

    public void setMinSelectedTokensForCompression(int minSelectedTokensForCompression) {
        validateGt(minSelectedTokensForCompression, "min_selected_tokens_for_compression");
        this.minSelectedTokensForCompression = minSelectedTokensForCompression;
    }

    public int getCompressionTargetTokens() {
        return compressionTargetTokens;
    }

    public void setCompressionTargetTokens(int compressionTargetTokens) {
        validateGt(compressionTargetTokens, "compression_target_tokens");
        this.compressionTargetTokens = compressionTargetTokens;
    }

    public int getSummaryMergeTargetTokens() {
        return summaryMergeTargetTokens;
    }

    public void setSummaryMergeTargetTokens(int summaryMergeTargetTokens) {
        validateGt(summaryMergeTargetTokens, "summary_merge_target_tokens");
        this.summaryMergeTargetTokens = summaryMergeTargetTokens;
    }

    public int getAccumulatedSummaryTokenLimit() {
        return accumulatedSummaryTokenLimit;
    }

    public void setAccumulatedSummaryTokenLimit(int accumulatedSummaryTokenLimit) {
        validateGt(accumulatedSummaryTokenLimit, "accumulated_summary_token_limit");
        this.accumulatedSummaryTokenLimit = accumulatedSummaryTokenLimit;
    }

    public int getSummaryMergeMinBlocks() {
        return summaryMergeMinBlocks;
    }

    public void setSummaryMergeMinBlocks(int summaryMergeMinBlocks) {
        if (summaryMergeMinBlocks < 2) {
            throw new IllegalArgumentException("summary_merge_min_blocks must be >= 2");
        }
        this.summaryMergeMinBlocks = summaryMergeMinBlocks;
    }

    public int getPriorContextWindowSize() {
        return priorContextWindowSize;
    }

    public void setPriorContextWindowSize(int priorContextWindowSize) {
        validateGt(priorContextWindowSize, "prior_context_window_size");
        this.priorContextWindowSize = priorContextWindowSize;
    }

    public String getCustomCompressionPrompt() {
        return customCompressionPrompt;
    }

    public void setCustomCompressionPrompt(String customCompressionPrompt) {
        this.customCompressionPrompt = customCompressionPrompt;
    }

    private static void validateGt(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be > 0");
        }
    }
}
