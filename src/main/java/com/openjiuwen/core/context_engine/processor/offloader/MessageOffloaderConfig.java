/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link MessageOffloader}.
 *
 * <p>Mirrors Python's {@code MessageOffloaderConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 */
public class MessageOffloaderConfig {
    private static final Set<String> VALID_ROLES = Set.of("user", "assistant", "tool");

    @JsonProperty("messages_threshold")
    private Integer messagesThreshold;

    @JsonProperty("tokens_threshold")
    private int tokensThreshold = 20000;

    @JsonProperty("large_message_threshold")
    private int largeMessageThreshold = 1000;

    @JsonProperty("offload_message_type")
    private List<String> offloadMessageType = List.of("tool");

    @JsonProperty("protected_tool_names")
    private List<String> protectedToolNames = List.of("reload_original_context_messages");

    @JsonProperty("trim_size")
    private int trimSize = 100;

    @JsonProperty("messages_to_keep")
    private Integer messagesToKeep;

    @JsonProperty("keep_last_round")
    private boolean keepLastRound = true;

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

    public int getTrimSize() {
        return trimSize;
    }

    public void setTrimSize(int trimSize) {
        validateGt(trimSize, "trim_size");
        this.trimSize = trimSize;
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
