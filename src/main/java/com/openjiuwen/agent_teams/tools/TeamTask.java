/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for task tables (one per session).
 * <p>
 * Mirrors Python's {@code TeamTaskBase} in {@code openjiuwen.agent_teams.tools.models}.
 * </p>
 */
public class TeamTask {

    private String taskId;
    private String teamName;
    private String title;
    private String content;
    private String status;
    private String assignee;
    private Long updatedAt;

    public TeamTask() {
    }

    public TeamTask(String taskId, String teamName, String title,
                    String content, String status, String assignee, Long updatedAt) {
        this.taskId = taskId;
        this.teamName = teamName;
        this.title = title;
        this.content = content;
        this.status = status;
        this.assignee = assignee;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Return a lightweight summary (id + title + status) for write-op responses.
     *
     * @return a map with task_id, title, and status
     */
    public Map<String, Object> brief() {
        Map<String, Object> result = new HashMap<>();
        result.put("task_id", taskId);
        result.put("title", title);
        result.put("status", status);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamTask that)) return false;
        return Objects.equals(taskId, that.taskId)
                && Objects.equals(teamName, that.teamName)
                && Objects.equals(title, that.title)
                && Objects.equals(content, that.content)
                && Objects.equals(status, that.status)
                && Objects.equals(assignee, that.assignee)
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, teamName, title, content, status, assignee, updatedAt);
    }

    @Override
    public String toString() {
        return "TeamTask{" +
                "taskId='" + taskId + '\'' +
                ", teamName='" + teamName + '\'' +
                ", title='" + title + '\'' +
                ", content='" + content + '\'' +
                ", status='" + status + '\'' +
                ", assignee='" + assignee + '\'' +
                ", updatedAt=" + updatedAt +
                '}';
    }
}