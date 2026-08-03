/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.schema;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Schema describing a workflow reference in agent configuration.
 * <p>
 * Mirrors Python's {@code WorkflowSchema} in
 * {@code openjiuwen/core/single_agent/legacy/schema.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSchema {

    @Builder.Default
    private String id = "";

    @Builder.Default
    private String name = "";

    @Builder.Default
    private String version = "1.0";

    @Builder.Default
    private String description = "";

    @Builder.Default
    @JsonProperty("inputs")
    @JsonAlias("inputParams")
    private Map<String, Object> inputParams = new LinkedHashMap<>();

    @JsonIgnore
    public Map<String, Object> getInputs() {
        return inputParams;
    }

    @JsonIgnore
    public void setInputs(Map<String, Object> inputs) {
        this.inputParams = inputs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(inputs);
    }
}
