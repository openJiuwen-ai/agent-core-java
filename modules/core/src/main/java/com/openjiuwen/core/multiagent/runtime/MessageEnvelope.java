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
 * 
 * @since 0.1.7
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
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * isP2p.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isP2p() {
        return recipient != null && !recipient.isBlank();
    }

    /**
     * isPubsub.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isPubsub() {
        return topicId != null && !topicId.isBlank();
    }
}
