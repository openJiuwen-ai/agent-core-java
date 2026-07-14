/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

/**
 * Backward-compatible config DTO for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code DialogueCompressorConfig} in
 * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
 */
public class DialogueCompressorConfig
        extends com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressorConfig {
    public DialogueCompressorConfig() {
    }

    public DialogueCompressorConfig(Integer messagesThreshold, int tokensThreshold, Integer messagesToKeep,
                                    boolean keepLastRound, int compressionTargetTokens,
                                    String customCompressionPrompt, ModelRequestConfig model,
                                    ModelClientConfig modelClient) {
        setMessagesThreshold(messagesThreshold);
        setTokensThreshold(tokensThreshold);
        setMessagesToKeep(messagesToKeep);
        setKeepLastRound(keepLastRound);
        setCompressionTargetTokens(compressionTargetTokens);
        setCustomCompressionPrompt(customCompressionPrompt);
        setModel(model);
        setModelClient(modelClient);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        setMessagesThreshold(getMessagesThreshold());
        setTokensThreshold(getTokensThreshold());
        setMessagesToKeep(getMessagesToKeep());
        setCompressionTargetTokens(getCompressionTargetTokens());
    }

    public static final class Builder {
        private Integer messagesThreshold;
        private int tokensThreshold = 10000;
        private Integer messagesToKeep;
        private boolean keepLastRound = true;
        private int compressionTargetTokens = 1800;
        private String customCompressionPrompt;
        private ModelRequestConfig model;
        private ModelClientConfig modelClient;

        private Builder() {
        }

        public Builder messagesThreshold(Integer messagesThreshold) {
            this.messagesThreshold = messagesThreshold;
            return this;
        }

        public Builder tokensThreshold(int tokensThreshold) {
            this.tokensThreshold = tokensThreshold;
            return this;
        }

        public Builder messagesToKeep(Integer messagesToKeep) {
            this.messagesToKeep = messagesToKeep;
            return this;
        }

        public Builder keepLastRound(boolean keepLastRound) {
            this.keepLastRound = keepLastRound;
            return this;
        }

        public Builder compressionTargetTokens(int compressionTargetTokens) {
            this.compressionTargetTokens = compressionTargetTokens;
            return this;
        }

        public Builder compressionTokenLimit(int compressionTokenLimit) {
            this.compressionTargetTokens = compressionTokenLimit;
            return this;
        }

        public Builder customCompressionPrompt(String customCompressionPrompt) {
            this.customCompressionPrompt = customCompressionPrompt;
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

        public DialogueCompressorConfig build() {
            return new DialogueCompressorConfig(messagesThreshold, tokensThreshold, messagesToKeep,
                    keepLastRound, compressionTargetTokens, customCompressionPrompt, model, modelClient);
        }
    }
}
