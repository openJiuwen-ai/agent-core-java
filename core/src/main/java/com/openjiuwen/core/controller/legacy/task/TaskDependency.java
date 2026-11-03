/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code TaskDependency} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskDependency {

    @JsonProperty("dependency_id")
    private String dependencyId;

    @JsonProperty("dependency_type")
    private DependencyType dependencyType = DependencyType.SEQUENTIAL;

    private String condition;

    @JsonProperty("data_mapping")
    private Map<String, String> dataMapping = new LinkedHashMap<>();

    private boolean required = true;

    public TaskDependency(String dependencyId) {
        this.dependencyId = dependencyId;
    }

    public TaskDependency(
            String dependencyId,
            DependencyType dependencyType,
            String condition,
            Map<String, String> dataMapping,
            boolean required
    ) {
        this.dependencyId = dependencyId;
        setDependencyType(dependencyType);
        this.condition = condition;
        setDataMapping(dataMapping);
        this.required = required;
    }

    public void setDependencyType(DependencyType dependencyType) {
        this.dependencyType = dependencyType == null ? DependencyType.SEQUENTIAL : dependencyType;
    }

    public void setDataMapping(Map<String, String> dataMapping) {
        this.dataMapping = dataMapping == null ? new LinkedHashMap<>() : new LinkedHashMap<>(dataMapping);
    }
}
