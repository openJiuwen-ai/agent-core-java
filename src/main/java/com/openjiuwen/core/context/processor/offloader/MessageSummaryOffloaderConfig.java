/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Backward-compatible config DTO for the pre-0.1.14 summary offloader package.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloaderConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_summary_offloader.py}.</p>
 */
public class MessageSummaryOffloaderConfig
        extends com.openjiuwen.core.context_engine.processor.offloader.MessageSummaryOffloaderConfig {
    private Integer messagesThreshold;
    private int tokensThreshold = 20000;
    private Integer messagesToKeep;
    private boolean keepLastRound = true;

    public MessageSummaryOffloaderConfig() {
    }

    public MessageSummaryOffloaderConfig(Integer messagesThreshold, int tokensThreshold, int largeMessageThreshold,
                                         List<String> offloadMessageType, List<String> protectedToolNames,
                                         Integer messagesToKeep, boolean keepLastRound, ModelRequestConfig model,
                                         ModelClientConfig modelClient, int summaryMaxTokens,
                                         boolean enablePreciseStep, int stepSummaryMaxContextMessages,
                                         int contentMaxCharsForCompression) {
        setMessagesThreshold(messagesThreshold);
        setTokensThreshold(tokensThreshold);
        setLargeMessageThreshold(largeMessageThreshold);
        setOffloadMessageType(offloadMessageType);
        setProtectedToolNames(protectedToolNames);
        setMessagesToKeep(messagesToKeep);
        setKeepLastRound(keepLastRound);
        setModel(model);
        setModelClient(modelClient);
        setSummaryMaxTokens(summaryMaxTokens);
        setEnablePreciseStep(enablePreciseStep);
        setStepSummaryMaxContextMessages(stepSummaryMaxContextMessages);
        setContentMaxCharsForCompression(contentMaxCharsForCompression);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getMessagesThreshold() {
        return messagesThreshold;
    }

    public void setMessagesThreshold(Integer messagesThreshold) {
        if (messagesThreshold != null && messagesThreshold <= 0) {
            throw new IllegalArgumentException("messagesThreshold must be > 0");
        }
        this.messagesThreshold = messagesThreshold;
    }

    public int getTokensThreshold() {
        return tokensThreshold;
    }

    public void setTokensThreshold(int tokensThreshold) {
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0");
        }
        this.tokensThreshold = tokensThreshold;
    }

    public Integer getMessagesToKeep() {
        return messagesToKeep;
    }

    public void setMessagesToKeep(Integer messagesToKeep) {
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0");
        }
        this.messagesToKeep = messagesToKeep;
    }

    public boolean isKeepLastRound() {
        return keepLastRound;
    }

    public void setKeepLastRound(boolean keepLastRound) {
        this.keepLastRound = keepLastRound;
    }

    public void validate() {
        setMessagesThreshold(messagesThreshold);
        setTokensThreshold(tokensThreshold);
        setLargeMessageThreshold(getLargeMessageThreshold());
        setOffloadMessageType(getOffloadMessageType());
        setProtectedToolNames(getProtectedToolNames());
        setMessagesToKeep(messagesToKeep);
        setSummaryMaxTokens(getSummaryMaxTokens());
        setStepSummaryMaxContextMessages(getStepSummaryMaxContextMessages());
        setContentMaxCharsForCompression(getContentMaxCharsForCompression());
    }

    public static final class Builder {
        private Integer messagesThreshold;
        private int tokensThreshold = 20000;
        private int largeMessageThreshold = 1000;
        private List<String> offloadMessageType = List.of("tool");
        private List<String> protectedToolNames = List.of("reload_original_context_messages");
        private Integer messagesToKeep;
        private boolean keepLastRound = true;
        private ModelRequestConfig model;
        private ModelClientConfig modelClient;
        private int summaryMaxTokens = 900;
        private boolean enablePreciseStep;
        private int stepSummaryMaxContextMessages = 8;
        private int contentMaxCharsForCompression = 200000;

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

        public Builder largeMessageThreshold(int largeMessageThreshold) {
            this.largeMessageThreshold = largeMessageThreshold;
            return this;
        }

        public Builder offloadMessageType(List<String> offloadMessageType) {
            this.offloadMessageType = offloadMessageType == null ? null : new ArrayList<>(offloadMessageType);
            return this;
        }

        public Builder protectedToolNames(List<String> protectedToolNames) {
            this.protectedToolNames = protectedToolNames == null ? null : new ArrayList<>(protectedToolNames);
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

        public Builder model(ModelRequestConfig model) {
            this.model = model;
            return this;
        }

        public Builder modelClient(ModelClientConfig modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder summaryMaxTokens(int summaryMaxTokens) {
            this.summaryMaxTokens = summaryMaxTokens;
            return this;
        }

        public Builder enablePreciseStep(boolean enablePreciseStep) {
            this.enablePreciseStep = enablePreciseStep;
            return this;
        }

        public Builder stepSummaryMaxContextMessages(int stepSummaryMaxContextMessages) {
            this.stepSummaryMaxContextMessages = stepSummaryMaxContextMessages;
            return this;
        }

        public Builder contentMaxCharsForCompression(int contentMaxCharsForCompression) {
            this.contentMaxCharsForCompression = contentMaxCharsForCompression;
            return this;
        }

        public MessageSummaryOffloaderConfig build() {
            return new MessageSummaryOffloaderConfig(messagesThreshold, tokensThreshold, largeMessageThreshold,
                    offloadMessageType, protectedToolNames, messagesToKeep, keepLastRound, model, modelClient,
                    summaryMaxTokens, enablePreciseStep, stepSummaryMaxContextMessages,
                    contentMaxCharsForCompression);
        }
    }
}
