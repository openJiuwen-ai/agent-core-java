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

/**
 * Public class TaskMutationResult used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskMutationResult {
    private TaskRecord task;
    private String reason;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TaskRecord> unblockedTasks = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TaskRecord> cancelledTasks = new ArrayList<>();

    /**
     * isSuccess.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSuccess() {
        return reason == null;
    }

    /**
     * success.
     * 
     * @param taskId taskId
     * @return the result
     * @since 0.1.7
     */
    public static TaskMutationResult success(String taskId) {
        TaskRecord record = new TaskRecord();
        record.setTaskId(taskId);
        return TaskMutationResult.builder().task(record).build();
    }

    /**
     * fail.
     * 
     * @param reason reason
     * @return the result
     * @since 0.1.7
     */
    public static TaskMutationResult fail(String reason) {
        return TaskMutationResult.builder().reason(reason).build();
    }
}
