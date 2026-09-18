/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.common.constants.TaskType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's {@code Task} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Task {

    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("task_id")
    private String taskId = "";

    @JsonProperty("task_type")
    private TaskType taskType = TaskType.UNDEFINED;

    private String description;

    private TaskStatus status = TaskStatus.PENDING;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    private TaskInput input = new TaskInput();

    private TaskResult result;

    private List<TaskDependency> dependencies = new ArrayList<>();

    private Set<String> dependents = new LinkedHashSet<>();

    @JsonProperty("parent_task_id")
    private String parentTaskId;

    @JsonProperty("child_task_ids")
    private Set<String> childTaskIds = new LinkedHashSet<>();

    @JsonProperty("group_id")
    private String groupId;

    private int level = 0;

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public void setInput(TaskInput input) {
        this.input = input == null ? new TaskInput() : input;
    }

    public void setDependencies(List<TaskDependency> dependencies) {
        this.dependencies = dependencies == null ? new ArrayList<>() : new ArrayList<>(dependencies);
    }

    public void setDependents(Set<String> dependents) {
        this.dependents = dependents == null ? new LinkedHashSet<>() : new LinkedHashSet<>(dependents);
    }

    public void setChildTaskIds(Set<String> childTaskIds) {
        this.childTaskIds = childTaskIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(childTaskIds);
    }
}
