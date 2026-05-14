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
 * Mirrors Python's {@code WorkflowSchema} used in application agent configs.
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
        this.inputParams = inputs;
    }

    public static WorkflowSchemaBuilder builder() {
        return new WorkflowSchemaBuilder();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public static final class WorkflowSchemaBuilder {
        private String id = "";
        private String name = "";
        private String version = "1.0";
        private String description = "";
        private Map<String, Object> inputParams = new LinkedHashMap<>();

        public WorkflowSchemaBuilder id(String id) { this.id = id; return this; }
        public WorkflowSchemaBuilder name(String name) { this.name = name; return this; }
        public WorkflowSchemaBuilder version(String version) { this.version = version; return this; }
        public WorkflowSchemaBuilder description(String description) { this.description = description; return this; }
        public WorkflowSchemaBuilder inputParams(Map<String, Object> inputParams) { this.inputParams = inputParams; return this; }

        public WorkflowSchema build() {
            WorkflowSchema schema = new WorkflowSchema();
            schema.setId(id);
            schema.setName(name);
            schema.setVersion(version);
            schema.setDescription(description);
            schema.setInputs(inputParams);
            return schema;
        }
    }
}
