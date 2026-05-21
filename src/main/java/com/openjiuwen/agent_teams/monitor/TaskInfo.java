// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_teams.monitor;

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
}