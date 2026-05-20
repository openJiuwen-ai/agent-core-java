/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.Builder;
import lombok.Data;

/**
 * Plain task dependency record matching Python TeamTaskDependencyBase fields.
 */
@Data
@Builder
public class TaskDependencyRecord {
    private String teamName;
    private String taskId;
    private String dependsOnTaskId;
    private boolean isResolved;
}
