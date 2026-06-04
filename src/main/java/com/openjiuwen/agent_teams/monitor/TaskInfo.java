// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.task.TaskDetail;
import com.openjiuwen.agent_teams.schema.task.TaskSummary;

import java.util.Map;

/**
 * Task information.
 * 
 * updated_at is the millisecond wall-clock timestamp of the most
 * recent status transition on this task. Its semantic meaning is tied
 * to the current status.
 * 
 * Mirrors Python's agent_teams.monitor.models.TaskInfo
 * 
 * @since 0.1.12
 */
public class TaskInfo {
    
    /** Task identifier */
    private String taskId;
    
    /** Team identifier */
    private String teamId;
    
    /** Task title */
    private String title;
    
    /** Task content/description */
    private String content;
    
    /** TaskStatus value */
    private String status;
    
    /** Assigned member */
    private String assignee;
    
    /** Last update timestamp in milliseconds */
    private Long updatedAt;
    
    public TaskInfo() {
    }
    
    public TaskInfo(String taskId, String teamId, String title, String content, String status) {
        this.taskId = taskId;
        this.teamId = teamId;
        this.title = title;
        this.content = content;
        this.status = status;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getTeamId() {
        return teamId;
    }
    
    public void setTeamId(String teamId) {
        this.teamId = teamId;
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
    
    public static TaskInfo fromInternal(Object task) {
        if (task instanceof TaskDetail detail) {
            TaskInfo info = fromSummary(detail);
            info.setContent(detail.getContent());
            info.setUpdatedAt(detail.getUpdatedAt());
            return info;
        }
        if (task instanceof TaskSummary summary) {
            return fromSummary(summary);
        }
        if (task instanceof Map<?, ?> map) {
            TaskInfo info = new TaskInfo(
                    stringValue(firstPresent(map, "task_id", "taskId")),
                    stringValue(firstPresent(map, "team_name", "teamName", "team_id", "teamId")),
                    stringValue(firstPresent(map, "title")),
                    stringValue(firstPresent(map, "content")),
                    stringValue(firstPresent(map, "status"))
            );
            info.setAssignee(stringValue(firstPresent(map, "assignee", "assignee_member_name", "assigneeMemberName")));
            info.setUpdatedAt(longValue(firstPresent(map, "updated_at", "updatedAt")));
            return info;
        }
        return null;
    }

    private static TaskInfo fromSummary(TaskSummary summary) {
        TaskInfo info = new TaskInfo(
                summary.getTaskId(),
                "",
                summary.getTitle(),
                "",
                summary.getStatus() != null ? summary.getStatus().name() : null
        );
        info.setAssignee(summary.getAssignee());
        return info;
    }

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
