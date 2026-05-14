/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a field to be extracted by the Questioner component.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldInfo {
    private String fieldName;
    private String description;
    @Builder.Default
    private String type = "string";
    @Builder.Default
    private String cnFieldName = "";
    @Builder.Default
    private boolean required = false;
    @Builder.Default
    private Object defaultValue = "";

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCnFieldName() {
        return cnFieldName;
    }

    public void setCnFieldName(String cnFieldName) {
        this.cnFieldName = cnFieldName;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
}
