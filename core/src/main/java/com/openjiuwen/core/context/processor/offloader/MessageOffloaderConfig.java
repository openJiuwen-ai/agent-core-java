/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Configuration for {@link MessageOffloader}.
 *
 * <p>Mirrors Python's {@code MessageOffloaderConfig} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 *
 * <p>v2 change (架构文档 §7.2.1): the message-count threshold
 * ({@code messages_threshold}) trigger was removed — offloading is driven by
 * token totals and per-message size only. The field and accessors below are
 * kept as deprecated no-ops purely for source/binary compatibility with the
 * legacy test project (jiuwen-test {@code agent_core_java_develop} branch)
 * and legacy JSON configs; they do not participate in trigger logic.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageOffloaderConfig {
    private static final Set<String> VALID_ROLES = Set.of("user", "assistant", "tool");

    @JsonProperty("tokens_threshold")
    private int tokensThreshold = 20000;

    @JsonProperty("large_message_threshold")
    private int largeMessageThreshold = 1000;

    /**
     * Deprecated trigger knob (v2 §7.2.1): the message-count trigger was
     * removed; this field is only parsed/serialized for config compatibility
     * and is never read by {@link MessageOffloader} trigger decisions.
     */
    @Deprecated
    @JsonProperty("messages_threshold")
    private Integer messagesThreshold;

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

    @JsonProperty("recent_message_protection_count")
    private int recentMessageProtectionCount = 5;

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

    /**
     * Returns the legacy message-count threshold (no-op in v2).
     * <p>
     * The message-count trigger was removed by v2 §7.2.1; this accessor is
     * kept only so legacy callers (old test project) compile. The value has
     * no effect on offload triggering.
     *
     * @return the legacy {@code messages_threshold} value, or {@code null} if unset; never affects offload triggering
     * @deprecated message-count trigger removed by v2 §7.2.1; kept only so
     *             legacy callers (old test project) compile. Setting it has
     *             no effect on offload triggering.
     */
    @Deprecated
    public Integer getMessagesThreshold() {
        return messagesThreshold;
    }

    /**
     * Sets the legacy message-count threshold (no-op in v2).
     * <p>
     * The message-count trigger was removed by v2 §7.2.1; this accessor is
     * kept only so legacy callers (old test project) compile. The value has
     * no effect on offload triggering.
     *
     * @param messagesThreshold the legacy threshold value to store; ignored by trigger logic
     * @deprecated message-count trigger removed by v2 §7.2.1; kept only so
     *             legacy callers (old test project) compile. Setting it has
     *             no effect on offload triggering.
     */
    @Deprecated
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

    public boolean isKeepLastRound() {
        return keepLastRound;
    }

    public void setKeepLastRound(boolean keepLastRound) {
        this.keepLastRound = keepLastRound;
    }

    public int getRecentMessageProtectionCount() {
        return recentMessageProtectionCount;
    }

    public void setRecentMessageProtectionCount(int recentMessageProtectionCount) {
        // 0 disables the recent-window protection (legacy behavior kept testable).
        if (recentMessageProtectionCount < 0) {
            throw new IllegalArgumentException("recent_message_protection_count must be >= 0");
        }
        this.recentMessageProtectionCount = recentMessageProtectionCount;
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
