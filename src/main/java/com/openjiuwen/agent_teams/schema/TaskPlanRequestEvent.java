/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Event published when a member submits an execution plan for approval.
 * <p>
 * Mirrors Python's {@code TaskPlanRequestEvent} in
 * {@code openjiuwen/agent_teams/schema/events.py}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TaskPlanRequestEvent extends BaseEventMessage {

    @JsonProperty("task_id")
    private String taskId;

    private String status = "claimed";

    @JsonProperty("plan_id")
    private String planId;

    @JsonProperty("member_plan_md")
    private String memberPlanMd;

    @JsonProperty("tool_call_id")
    private String toolCallId = "";
}
