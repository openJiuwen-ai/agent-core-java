/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TaskMutationResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TaskMutationResult {
    private TaskRecord task;
    @Builder.Default
    private List<TaskRecord> unblockedTasks = new ArrayList<>();
    @Builder.Default
    private List<TaskRecord> cancelledTasks = new ArrayList<>();
}
