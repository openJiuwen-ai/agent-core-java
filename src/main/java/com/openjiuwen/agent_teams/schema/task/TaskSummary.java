/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight task summary.
 *
 * <p>Mirrors Python's {@code TaskSummary} in
 * {@code openjiuwen.agent_teams.schema.task}.
 */
public class TaskSummary {

    private final String taskId;
    private final String title;
    private final TaskStatus status;
    private final String assignee;
    private final List<String> blockedBy;

    public TaskSummary(String taskId, String title, TaskStatus status, String assignee, List<String> blockedBy) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
        this.assignee = assignee;
        this.blockedBy = blockedBy != null ? new ArrayList<>(blockedBy) : new ArrayList<>();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getAssignee() {
        return assignee;
    }

    public List<String> getBlockedBy() {
        return new ArrayList<>(blockedBy);
    }
}
