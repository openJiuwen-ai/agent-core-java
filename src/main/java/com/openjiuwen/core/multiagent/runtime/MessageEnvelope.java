/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight message container aligned with Python's
 * {@code multi_agent.team_runtime.envelope.MessageEnvelope}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageEnvelope {
    private String messageId;
    private Object message;
    private String sender;
    private String recipient;
    private String topicId;
    private String sessionId;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isP2p() {
        return recipient != null && !recipient.isBlank();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isPubsub() {
        return topicId != null && !topicId.isBlank();
    }
}
