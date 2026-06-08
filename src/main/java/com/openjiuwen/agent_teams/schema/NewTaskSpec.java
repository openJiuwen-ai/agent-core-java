/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A task to be created via dependency-graph mutation.
 *
 * <p>Mirrors Python's {@code NewTaskSpec} in
 * {@code openjiuwen/agent_teams/schema/task.py}.</p>
 */
public record NewTaskSpec(
        @JsonProperty("task_id") String taskId,
        String title,
        String content,
        @JsonProperty("initial_status") String initialStatus
) {
}
