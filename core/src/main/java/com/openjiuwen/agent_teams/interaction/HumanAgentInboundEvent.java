/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Notification that a team-side message reached a human agent.
 *
 * <p>Mirrors Python's {@code HumanAgentInboundEvent} in
 * {@code openjiuwen/agent_teams/interaction/payload.py}.</p>
 */
public record HumanAgentInboundEvent(
        @JsonProperty("member_name") String memberName,
        String sender,
        String body,
        boolean broadcast,
        @JsonProperty("message_id") String messageId,
        long timestamp
) {
}
