/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when the leader approves or rejects a member execution plan.
 * <p>
 * Mirrors Python's {@code TaskPlanResponseEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskPlanResponseEvent extends BaseEventMessage {

    @JsonProperty("task_id")
    private String taskId;

    private boolean approved;
    private String status;

    @JsonProperty("plan_id")
    private String planId;

    private String feedback = "";

    @JsonProperty("tool_call_id")
    private String toolCallId = "";
}
