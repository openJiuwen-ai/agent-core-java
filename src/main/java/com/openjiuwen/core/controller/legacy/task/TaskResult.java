/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code TaskResult} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResult {

    private TaskStatus status;

    private Object output;

    private String error;

    private Map<String, Object> metadata = new LinkedHashMap<>();

    public TaskResult(TaskStatus status, Object output, String error, Map<String, Object> metadata) {
        this.status = status;
        this.output = output;
        this.error = error;
        setMetadata(metadata);
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
