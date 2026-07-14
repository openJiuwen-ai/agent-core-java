/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import java.util.ArrayList;
import java.util.List;

/**
 * Backward-compatible config DTO for the pre-0.1.14 offloader package.
 *
 * <p>Mirrors Python's {@code MessageOffloaderConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 */
public class MessageOffloaderConfig
        extends com.openjiuwen.core.context_engine.processor.offloader.MessageOffloaderConfig {
    public MessageOffloaderConfig() {
    }

    public MessageOffloaderConfig(Integer messagesThreshold, int tokensThreshold, int largeMessageThreshold,
                                  List<String> offloadMessageType, List<String> protectedToolNames,
                                  int trimSize, Integer messagesToKeep, boolean keepLastRound) {
        setMessagesThreshold(messagesThreshold);
        setTokensThreshold(tokensThreshold);
        setLargeMessageThreshold(largeMessageThreshold);
        setOffloadMessageType(offloadMessageType);
        setProtectedToolNames(protectedToolNames);
        setTrimSize(trimSize);
        setMessagesToKeep(messagesToKeep);
        setKeepLastRound(keepLastRound);
    }

    public static Builder builder() {
        return new Builder();
    }

    public void validate() {
        setMessagesThreshold(getMessagesThreshold());
        setTokensThreshold(getTokensThreshold());
        setLargeMessageThreshold(getLargeMessageThreshold());
        setOffloadMessageType(getOffloadMessageType());
        setProtectedToolNames(getProtectedToolNames());
        setTrimSize(getTrimSize());
        setMessagesToKeep(getMessagesToKeep());
    }

    public static final class Builder {
        private Integer messagesThreshold;
        private int tokensThreshold = 20000;
        private int largeMessageThreshold = 1000;
        private List<String> offloadMessageType = List.of("tool");
        private List<String> protectedToolNames = List.of("reload_original_context_messages");
        private int trimSize = 100;
        private Integer messagesToKeep;
        private boolean keepLastRound = true;

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

        public Builder trimSize(int trimSize) {
            this.trimSize = trimSize;
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

        public MessageOffloaderConfig build() {
            return new MessageOffloaderConfig(messagesThreshold, tokensThreshold, largeMessageThreshold,
                    offloadMessageType, protectedToolNames, trimSize, messagesToKeep, keepLastRound);
        }
    }
}
