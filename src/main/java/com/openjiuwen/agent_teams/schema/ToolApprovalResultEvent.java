/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when the leader approves or rejects one tool call.
 * <p>
 * Mirrors Python's {@code ToolApprovalResultEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolApprovalResultEvent extends BaseEventMessage {

    @JsonProperty("tool_call_id")
    private String toolCallId;

    private boolean approved;
    private String feedback = "";

    @JsonProperty("auto_confirm")
    private boolean autoConfirm = false;
}
