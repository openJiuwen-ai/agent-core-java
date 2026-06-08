/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight task summary returned by list and claimable actions.
 *
 * <p>Mirrors Python's {@code TaskSummary} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskSummary {

    @JsonProperty("task_id")
    private String taskId;

    private String title;

    private String status;

    private String assignee;

    @JsonProperty("blocked_by")
    private List<String> blockedBy = new ArrayList<>();

    @JsonProperty("updated_at")
    private Long updatedAt;
}
