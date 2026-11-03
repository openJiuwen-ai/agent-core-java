/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-node exception handling configuration.
 * <p>
 * Mirrors Python's {@code ExceptionConfig} in
 * {@code openjiuwen/core/workflow/workflow_config.py}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExceptionConfig {

    private static final String HANDLE_TYPE_FIELD = "handle_type";

    @JsonProperty(HANDLE_TYPE_FIELD)
    private String handleType = "interrupt";

    private final Map<String, Object> extraFields = new LinkedHashMap<>();

    public ExceptionConfig() {
    }

    public ExceptionConfig(String handleType) {
        setHandleType(handleType);
    }

    public String getHandleType() {
        return handleType;
    }

    public void setHandleType(String handleType) {
        this.handleType = Objects.requireNonNull(handleType, "handle_type must not be null");
    }

    @JsonAnySetter
    public void putExtraField(String key, Object value) {
        if (HANDLE_TYPE_FIELD.equals(key)) {
            setHandleType(String.valueOf(value));
            return;
        }
        extraFields.put(key, value);
    }

    public void setExtraFields(Map<String, Object> extraFields) {
        this.extraFields.clear();
        if (extraFields != null) {
            for (Map.Entry<String, Object> entry : extraFields.entrySet()) {
                putExtraField(entry.getKey(), entry.getValue());
            }
        }
    }

    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields;
    }
}
