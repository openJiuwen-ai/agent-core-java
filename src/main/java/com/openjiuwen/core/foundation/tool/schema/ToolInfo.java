/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Tool information descriptor for LLM function calling.
 * <p>
 * Mirrors Python's {@code ToolInfo} model from the foundation tool schema.
 * Follows the OpenAI function calling format.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolInfo {

    /** Tool type, defaults to "function". */
    @Builder.Default
    private String type = "function";

    /** Tool name. */
    @Builder.Default
    private String name = "";

    /** Tool description. */
    @Builder.Default
    private String description = "";

    /**
     * Parameter schema — follows JSON Schema format.
     * <p>
     * Example: {@code {"type": "object", "properties": {"query": {"type": "string"}}}}
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();

    public static ToolInfoBuilder builder() {
        return new ToolInfoBuilder();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public static class ToolInfoBuilder {
        private String type = "function";
        private String name = "";
        private String description = "";
        private Map<String, Object> parameters = Map.of();

        public ToolInfoBuilder type(String type) {
            this.type = type;
            return this;
        }

        public ToolInfoBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ToolInfoBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ToolInfoBuilder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public ToolInfo build() {
            ToolInfo info = new ToolInfo();
            info.setType(type);
            info.setName(name);
            info.setDescription(description);
            info.setParameters(parameters);
            return info;
        }
    }
}
