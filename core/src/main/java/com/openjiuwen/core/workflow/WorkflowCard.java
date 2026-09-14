/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code WorkflowCard} in
 * {@code openjiuwen/core/workflow/base.py}.
 */
public class WorkflowCard extends BaseCard {

    private String version = "";
    private Object inputParams;

    public WorkflowCard() {
        super();
    }

    public WorkflowCard(String id, String name) {
        super(id, name, "");
    }

    public WorkflowCard(String id, String name, String version, String description) {
        super(id, name, description);
        this.version = version == null ? "" : version;
    }

    public WorkflowCard(String id, String name, String description, String version, Object inputParams) {
        super(id, name, description);
        this.version = version == null ? "" : version;
        this.inputParams = inputParams;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version == null ? "" : version;
    }

    public Object getInputParams() {
        return inputParams;
    }

    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolInfo toolInfo() {
        Map<String, Object> parameters = resolveInputParamsSchema();
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(parameters)
                .build();
    }

    public String str() {
        return toString();
    }

    public String toStr() {
        return toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInputParamsSchema() {
        if (inputParams == null) {
            return Map.of();
        }
        if (inputParams instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (inputParams instanceof Class<?> clazz) {
            return SchemaUtils.getSchemaDict(clazz);
        }
        return Map.of();
    }

    @Override
    public String toString() {
        return "WorkflowCard(super=" + super.toString()
                + ", version=" + version
                + ", inputParams=" + inputParams
                + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WorkflowCard that)) {
            return false;
        }
        return Objects.equals(getId(), that.getId())
                && Objects.equals(getName(), that.getName())
                && Objects.equals(getDescription(), that.getDescription())
                && Objects.equals(version, that.version)
                && Objects.equals(inputParams, that.inputParams);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getName(), getDescription(), version, inputParams);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private String version = "";
        private Object inputParams;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        public WorkflowCard build() {
            return new WorkflowCard(id, name, description, version, inputParams);
        }
    }
}
