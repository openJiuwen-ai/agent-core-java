/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import java.util.Objects;

/**
 * Base class for task dependency tables (one per session).
 * <p>
 * Mirrors Python's {@code TeamTaskDependencyBase} in
 * {@code openjiuwen/agent_teams/tools/models.py}.
 */
public class TeamTaskDependency {

    private String taskId;
    private String dependsOnTaskId;
    private String teamName;
    private Boolean resolved;

    public TeamTaskDependency() {
    }

    public TeamTaskDependency(String taskId, String dependsOnTaskId,
            String teamName, Boolean resolved) {
        this.taskId = taskId;
        this.dependsOnTaskId = dependsOnTaskId;
        this.teamName = teamName;
        this.resolved = resolved;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDependsOnTaskId() {
        return dependsOnTaskId;
    }

    public void setDependsOnTaskId(String dependsOnTaskId) {
        this.dependsOnTaskId = dependsOnTaskId;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        this.resolved = resolved;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TeamTaskDependency that)) {
            return false;
        }
        return Objects.equals(taskId, that.taskId)
                && Objects.equals(dependsOnTaskId, that.dependsOnTaskId)
                && Objects.equals(teamName, that.teamName)
                && Objects.equals(resolved, that.resolved);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, dependsOnTaskId, teamName, resolved);
    }

    @Override
    public String toString() {
        return "TeamTaskDependency{"
                + "taskId='" + taskId + '\''
                + ", dependsOnTaskId='" + dependsOnTaskId + '\''
                + ", teamName='" + teamName + '\''
                + ", resolved=" + resolved
                + '}';
    }
}
