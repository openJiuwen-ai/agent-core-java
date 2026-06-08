/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

/**
 * Mirrors Python's {@code TaskInput} in
 * {@code openjiuwen/core/controller/legacy/task/task.py}.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskInput {

    @JsonProperty("target_id")
    private String targetId = "";

    @JsonProperty("target_name")
    private String targetName = "";

    private Object arguments = new LinkedHashMap<>();

    public TaskInput(String targetId, String targetName, Object arguments) {
        this.targetId = targetId == null ? "" : targetId;
        this.targetName = targetName == null ? "" : targetName;
        setArguments(arguments);
    }

    public void setArguments(Object arguments) {
        this.arguments = arguments == null ? new LinkedHashMap<>() : arguments;
    }
}
