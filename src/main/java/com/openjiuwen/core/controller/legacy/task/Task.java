  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.controller.legacy.task;

import com.openjiuwen.core.common.constants.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legacy task model for controller compatibility.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    private String agentId;

    @Builder.Default
    private String taskId = "";

    @Builder.Default
    private TaskType taskType = TaskType.UNDEFINED;

    private String description;

    @Builder.Default
    private TaskStatus status = TaskStatus.PENDING;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Builder.Default
    private TaskInput input = new TaskInput();

    private TaskResult result;

    @Builder.Default
    private List<TaskDependency> dependencies = new ArrayList<>();

    @Builder.Default
    private Set<String> dependents = new LinkedHashSet<>();

    private String parentTaskId;

    @Builder.Default
    private Set<String> childTaskIds = new LinkedHashSet<>();

    private String groupId;

    @Builder.Default
    private int level = 0;

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        INTERRUPTED
    }

    public enum DependencyType {
        SEQUENTIAL,
        PARALLEL,
        CONDITIONAL,
        DATA
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDependency {
        private String dependencyId;
        private DependencyType dependencyType = DependencyType.SEQUENTIAL;
        private String condition;
        private Map<String, String> dataMapping = new LinkedHashMap<>();
        private boolean required = true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInput {
        private String targetId = "";
        private String targetName = "";
        private Object arguments = new LinkedHashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResult {
        private TaskStatus status;
        private Object output;
        private String error;
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }
}
