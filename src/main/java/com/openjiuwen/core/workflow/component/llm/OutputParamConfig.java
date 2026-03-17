/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow.component.llm;

import java.util.Map;

/**
 * Configuration model for a single LLM output parameter.
 * <p>
 * Mirrors Python's {@code OutputParamConfig} Pydantic model with aliases:
 * {@code type}, {@code description}, {@code required}.
 */
public class OutputParamConfig {

    private String paramType = "";
    private String paramDescription = "";
    private boolean paramRequired = false;

    public OutputParamConfig() {
    }

    public OutputParamConfig(String paramType, String paramDescription, boolean paramRequired) {
        this.paramType = paramType != null ? paramType : "";
        this.paramDescription = paramDescription != null ? paramDescription : "";
        this.paramRequired = paramRequired;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType != null ? paramType : "";
    }

    public String getParamDescription() {
        return paramDescription;
    }

    public void setParamDescription(String paramDescription) {
        this.paramDescription = paramDescription != null ? paramDescription : "";
    }

    public boolean isParamRequired() {
        return paramRequired;
    }

    public void setParamRequired(boolean paramRequired) {
        this.paramRequired = paramRequired;
    }

    /**
     * Validate and create from a map (uses aliased keys: "type", "description", "required").
     * Mirrors Python's {@code OutputParamConfig.model_validate(dict)}.
     */
    public static OutputParamConfig fromMap(Map<String, Object> map) {
        if (map == null) {
            throw new IllegalArgumentException("output param config map must not be null");
        }
        String type = map.containsKey("type") ? String.valueOf(map.get("type")) : "";
        String description = map.containsKey("description") ? String.valueOf(map.get("description")) : "";
        boolean required = false;
        Object reqVal = map.get("required");
        if (reqVal instanceof Boolean b) {
            required = b;
        } else if (reqVal instanceof String s) {
            required = Boolean.parseBoolean(s);
        }
        return new OutputParamConfig(type, description, required);
    }
}
