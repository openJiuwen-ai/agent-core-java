/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context_engine.processor.offloader;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Per-round budget control configuration for large tool results.
 *
 * <p>Mirrors Python's {@code ToolResultBudgetProcessorConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/tool_result_budget_processor.py}.</p>
 */
public class ToolResultBudgetProcessorConfig {
    private static final Set<String> VALID_ROLES = Set.of("tool");

    @JsonProperty("tokens_threshold")
    private int tokensThreshold = 50000;

    @JsonProperty("large_message_threshold")
    private int largeMessageThreshold = 10000;

    @JsonProperty("trim_size")
    private int trimSize = 3000;

    @JsonProperty("tool_name_allowlist")
    private List<String> toolNameAllowlist;

    @JsonProperty("offload_message_type")
    private List<String> offloadMessageType = List.of("tool");

    @JsonProperty("offload_file_prefix")
    private String offloadFilePrefix = "ToolResultBudgetProcessor";

    @JsonProperty("messages_threshold")
    private Integer messagesThreshold;

    @JsonProperty("messages_to_keep")
    private Integer messagesToKeep;

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

    public int getTrimSize() {
        return trimSize;
    }

    public void setTrimSize(int trimSize) {
        validateGt(trimSize, "trim_size");
        this.trimSize = trimSize;
    }

    public List<String> getToolNameAllowlist() {
        return toolNameAllowlist == null ? null : new ArrayList<>(toolNameAllowlist);
    }

    public void setToolNameAllowlist(List<String> toolNameAllowlist) {
        this.toolNameAllowlist = toolNameAllowlist == null ? null : new ArrayList<>(toolNameAllowlist);
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

    public String getOffloadFilePrefix() {
        return offloadFilePrefix;
    }

    public void setOffloadFilePrefix(String offloadFilePrefix) {
        this.offloadFilePrefix = offloadFilePrefix;
    }

    public Integer getMessagesThreshold() {
        return messagesThreshold;
    }

    public void setMessagesThreshold(Integer messagesThreshold) {
        validateNullableGt(messagesThreshold, "messages_threshold");
        this.messagesThreshold = messagesThreshold;
    }

    public Integer getMessagesToKeep() {
        return messagesToKeep;
    }

    public void setMessagesToKeep(Integer messagesToKeep) {
        validateNullableGt(messagesToKeep, "messages_to_keep");
        this.messagesToKeep = messagesToKeep;
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
