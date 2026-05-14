/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal in-memory task record for the minimal Java team backend.
 *
 * <p>Mirrors the core runtime data carried by Python team task rows and
 * task schemas in {@code openjiuwen.agent_teams.tools.task_manager} and
 * {@code openjiuwen.agent_teams.schema.task}.
 */
public class TaskRecord {

    private final String taskId;
    private String title;
    private String content;
    private TaskStatus status;
    private String assignee;
    private final List<String> blockedBy = new ArrayList<>();
    private final List<String> blocks = new ArrayList<>();
    private long updatedAt;

    public TaskRecord(String taskId, String title, String content, TaskStatus status) {
        this.taskId = taskId;
        this.title = title;
        this.content = content;
        this.status = status;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
        touch();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        touch();
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        touch();
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
        touch();
    }

    public List<String> getBlockedBy() {
        return blockedBy;
    }

    public List<String> getBlocks() {
        return blocks;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public TaskSummary toSummary() {
        return new TaskSummary(taskId, title, status, assignee, blockedBy);
    }

    public TaskDetail toDetail() {
        return new TaskDetail(taskId, title, content, status, assignee, blockedBy, blocks, updatedAt);
    }

    private void touch() {
        this.updatedAt = System.currentTimeMillis();
    }
}
