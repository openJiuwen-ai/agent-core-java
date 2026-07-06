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
    private String reason;
    @Builder.Default
    private List<TaskRecord> unblockedTasks = new ArrayList<>();
    @Builder.Default
    private List<TaskRecord> cancelledTasks = new ArrayList<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSuccess() {
        return reason == null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskMutationResult success(String taskId) {
        TaskRecord record = new TaskRecord();
        record.setTaskId(taskId);
        return TaskMutationResult.builder().task(record).build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static TaskMutationResult fail(String reason) {
        return TaskMutationResult.builder().reason(reason).build();
    }
}
