/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Configuration for the {@link MessageOffloader} ContextProcessor.
 * <p>
 * The offloader keeps conversation history within safe memory/token limits
 * by trimming or offloading messages once thresholds are exceeded.
 * <p>
 * Mirrors Python's {@code MessageOffloaderConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageOffloaderConfig {

    /**
     * Maximum number of messages allowed before offloading is triggered.
     */
    private Integer messagesThreshold;

    /**
     * Maximum accumulated token count before offloading is triggered.
     */
    @Builder.Default
    private int tokensThreshold = 20000;

    /**
     * Messages whose token count exceeds this value are considered 'large'.
     */
    @Builder.Default
    private int largeMessageThreshold = 1000;

    /**
     * Roles eligible for offloading (e.g., "user", "assistant", "tool").
     */
    @Builder.Default
    private List<String> offloadMessageType = List.of("tool");

    /**
     * Number of tokens to retain when a message is offloaded.
     */
    @Builder.Default
    private int trimSize = 100;

    /**
     * Number of most-recent messages to retain regardless of thresholds.
     */
    private Integer messagesToKeep;

    /**
     * If true, the most recent user-assistant round is always preserved.
     */
    @Builder.Default
    private boolean keepLastRound = true;

    public static MessageOffloaderConfigBuilder builder() {
        return new MessageOffloaderConfigBuilder();
    }

    public Integer getMessagesThreshold() { return messagesThreshold; }
    public void setMessagesThreshold(Integer messagesThreshold) { this.messagesThreshold = messagesThreshold; }
    public int getTokensThreshold() { return tokensThreshold; }
    public void setTokensThreshold(int tokensThreshold) { this.tokensThreshold = tokensThreshold; }
    public int getLargeMessageThreshold() { return largeMessageThreshold; }
    public void setLargeMessageThreshold(int largeMessageThreshold) { this.largeMessageThreshold = largeMessageThreshold; }
    public List<String> getOffloadMessageType() { return offloadMessageType; }
    public void setOffloadMessageType(List<String> offloadMessageType) { this.offloadMessageType = offloadMessageType; }
    public int getTrimSize() { return trimSize; }
    public void setTrimSize(int trimSize) { this.trimSize = trimSize; }
    public Integer getMessagesToKeep() { return messagesToKeep; }
    public void setMessagesToKeep(Integer messagesToKeep) { this.messagesToKeep = messagesToKeep; }
    public boolean isKeepLastRound() { return keepLastRound; }
    public void setKeepLastRound(boolean keepLastRound) { this.keepLastRound = keepLastRound; }

    /**
     * Validate configuration constraints matching Python Pydantic {@code Field(gt=0)} rules.
     */
    public void validate() {
        if (messagesThreshold != null && messagesThreshold <= 0) {
            throw new IllegalArgumentException("messagesThreshold must be > 0, got " + messagesThreshold);
        }
        if (tokensThreshold <= 0) {
            throw new IllegalArgumentException("tokensThreshold must be > 0, got " + tokensThreshold);
        }
        if (largeMessageThreshold <= 0) {
            throw new IllegalArgumentException("largeMessageThreshold must be > 0, got " + largeMessageThreshold);
        }
        if (trimSize <= 0) {
            throw new IllegalArgumentException("trimSize must be > 0, got " + trimSize);
        }
        if (messagesToKeep != null && messagesToKeep <= 0) {
            throw new IllegalArgumentException("messagesToKeep must be > 0, got " + messagesToKeep);
        }
    }

    public static final class MessageOffloaderConfigBuilder {
        private Integer messagesThreshold;
        private int tokensThreshold = 20000;
        private int largeMessageThreshold = 1000;
        private List<String> offloadMessageType = List.of("tool");
        private int trimSize = 100;
        private Integer messagesToKeep;
        private boolean keepLastRound = true;

        public MessageOffloaderConfigBuilder messagesThreshold(Integer messagesThreshold) { this.messagesThreshold = messagesThreshold; return this; }
        public MessageOffloaderConfigBuilder tokensThreshold(int tokensThreshold) { this.tokensThreshold = tokensThreshold; return this; }
        public MessageOffloaderConfigBuilder largeMessageThreshold(int largeMessageThreshold) { this.largeMessageThreshold = largeMessageThreshold; return this; }
        public MessageOffloaderConfigBuilder offloadMessageType(List<String> offloadMessageType) { this.offloadMessageType = offloadMessageType; return this; }
        public MessageOffloaderConfigBuilder trimSize(int trimSize) { this.trimSize = trimSize; return this; }
        public MessageOffloaderConfigBuilder messagesToKeep(Integer messagesToKeep) { this.messagesToKeep = messagesToKeep; return this; }
        public MessageOffloaderConfigBuilder keepLastRound(boolean keepLastRound) { this.keepLastRound = keepLastRound; return this; }

        public MessageOffloaderConfig build() {
            return new MessageOffloaderConfig(messagesThreshold, tokensThreshold, largeMessageThreshold, offloadMessageType, trimSize, messagesToKeep, keepLastRound);
        }
    }
}
