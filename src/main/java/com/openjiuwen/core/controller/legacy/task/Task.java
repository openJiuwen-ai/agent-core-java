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
 * 
 * @since 0.1.7
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
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Builder.Default
    /**
     * TaskInput.
     * 
     * @since 0.1.7
     */
    private TaskInput input = new TaskInput();

    private TaskResult result;

    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TaskDependency> dependencies = new ArrayList<>();

    @Builder.Default
    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private Set<String> dependents = new LinkedHashSet<>();

    private String parentTaskId;

    @Builder.Default
    /**
     * LinkedHashSet<>.
     * 
     * @since 0.1.7
     */
    private Set<String> childTaskIds = new LinkedHashSet<>();

    private String groupId;

    @Builder.Default
    private int level = 0;

    /**
     * setAgentId.
     * 
     * @param agentId agentId
     * @since 0.1.7
     */
    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    /**
     * TaskStatus.
     * 
     * @since 0.1.7
     */
    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED,
        INTERRUPTED
    }

    /**
     * DependencyType.
     * 
     * @since 0.1.7
     */
    public enum DependencyType {
        SEQUENTIAL,
        PARALLEL,
        CONDITIONAL,
        DATA
    }

    /**
     * TaskDependency.
     * 
     * @since 0.1.7
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDependency {
        private String dependencyId;
        private DependencyType dependencyType = DependencyType.SEQUENTIAL;
        private String condition;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private Map<String, String> dataMapping = new LinkedHashMap<>();
        private boolean required = true;
    }

    /**
     * TaskInput.
     * 
     * @since 0.1.7
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskInput {
        private String targetId = "";
        private String targetName = "";

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private Object arguments = new LinkedHashMap<>();
    }

    /**
     * TaskResult.
     * 
     * @since 0.1.7
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskResult {
        private TaskStatus status;
        private Object output;
        private String error;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }
}
