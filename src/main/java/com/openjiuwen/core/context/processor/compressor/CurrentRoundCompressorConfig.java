/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Backward-compatible config DTO for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code CurrentRoundCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
 */
public class CurrentRoundCompressorConfig
        extends com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressorConfig {
    public CurrentRoundCompressorConfig() {
    }

    public CurrentRoundCompressorConfig(int tokensThreshold, int messagesToKeep, ModelRequestConfig model,
                                        ModelClientConfig modelClient, int minSelectedTokensForCompression,
                                        int compressionTargetTokens, int summaryMergeTargetTokens,
                                        int accumulatedSummaryTokenLimit, int summaryMergeMinBlocks,
                                        int priorContextWindowSize, String customCompressionPrompt) {
        setTokensThreshold(tokensThreshold);
        setMessagesToKeep(messagesToKeep);
        setModel(model);
        setModelClient(modelClient);
        setMinSelectedTokensForCompression(minSelectedTokensForCompression);
        setCompressionTargetTokens(compressionTargetTokens);
        setSummaryMergeTargetTokens(summaryMergeTargetTokens);
        setAccumulatedSummaryTokenLimit(accumulatedSummaryTokenLimit);
        setSummaryMergeMinBlocks(summaryMergeMinBlocks);
        setPriorContextWindowSize(priorContextWindowSize);
        setCustomCompressionPrompt(customCompressionPrompt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        setTokensThreshold(getTokensThreshold());
        setMessagesToKeep(getMessagesToKeep());
        setMinSelectedTokensForCompression(getMinSelectedTokensForCompression());
        setCompressionTargetTokens(getCompressionTargetTokens());
        setSummaryMergeTargetTokens(getSummaryMergeTargetTokens());
        setAccumulatedSummaryTokenLimit(getAccumulatedSummaryTokenLimit());
        setSummaryMergeMinBlocks(getSummaryMergeMinBlocks());
        setPriorContextWindowSize(getPriorContextWindowSize());
    }

    public static final class Builder {
        private int tokensThreshold = 100000;
        private int messagesToKeep = 3;
        private ModelRequestConfig model;
        private ModelClientConfig modelClient;
        private int minSelectedTokensForCompression = 20000;
        private int compressionTargetTokens = 4000;
        private int summaryMergeTargetTokens = 4000;
        private int accumulatedSummaryTokenLimit = 20000;
        private int summaryMergeMinBlocks = 3;
        private int priorContextWindowSize = 10;
        private String customCompressionPrompt;

        private Builder() {
        }

        public Builder tokensThreshold(int tokensThreshold) {
            this.tokensThreshold = tokensThreshold;
            return this;
        }

        public Builder largeMessageThreshold(int largeMessageThreshold) {
            this.tokensThreshold = largeMessageThreshold;
            return this;
        }

        public Builder messagesToKeep(int messagesToKeep) {
            this.messagesToKeep = messagesToKeep;
            return this;
        }

        public Builder model(ModelRequestConfig model) {
            this.model = model;
            return this;
        }

        public Builder modelClient(ModelClientConfig modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder minSelectedTokensForCompression(int minSelectedTokensForCompression) {
            this.minSelectedTokensForCompression = minSelectedTokensForCompression;
            return this;
        }

        public Builder compressionTargetTokens(int compressionTargetTokens) {
            this.compressionTargetTokens = compressionTargetTokens;
            return this;
        }

        public Builder summaryMergeTargetTokens(int summaryMergeTargetTokens) {
            this.summaryMergeTargetTokens = summaryMergeTargetTokens;
            return this;
        }

        public Builder accumulatedSummaryTokenLimit(int accumulatedSummaryTokenLimit) {
            this.accumulatedSummaryTokenLimit = accumulatedSummaryTokenLimit;
            return this;
        }

        public Builder summaryMergeMinBlocks(int summaryMergeMinBlocks) {
            this.summaryMergeMinBlocks = summaryMergeMinBlocks;
            return this;
        }

        public Builder priorContextWindowSize(int priorContextWindowSize) {
            this.priorContextWindowSize = priorContextWindowSize;
            return this;
        }

        public Builder customCompressionPrompt(String customCompressionPrompt) {
            this.customCompressionPrompt = customCompressionPrompt;
            return this;
        }

        public CurrentRoundCompressorConfig build() {
            return new CurrentRoundCompressorConfig(tokensThreshold, messagesToKeep, model, modelClient,
                    minSelectedTokensForCompression, compressionTargetTokens, summaryMergeTargetTokens,
                    accumulatedSummaryTokenLimit, summaryMergeMinBlocks, priorContextWindowSize,
                    customCompressionPrompt);
        }
    }
}
