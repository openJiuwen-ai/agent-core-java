/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a tool call from LLM output.
 * <p>
 * Mirrors Python's {@code ToolCall} model from the foundation LLM schema.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/chat/object">OpenAI Tool Call</a>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall {

    /** Tool call ID. */
    private String id;

    /** Tool call type (e.g., "function"). */
    @Builder.Default
    private String type = "function";

    /** Tool name. */
    private String name;

    /** Tool arguments as JSON string. */
    private String arguments;

    /** Tool call index, used to distinguish multiple tool calls in a single response. */
    @JsonProperty("index")
    private Integer index;

    public static ToolCallBuilder builder() {
        return new ToolCallBuilder();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public static final class ToolCallBuilder {
        private String id;
        private String type = "function";
        private String name;
        private String arguments;
        private Integer index;

        public ToolCallBuilder id(String id) {
            this.id = id;
            return this;
        }

        public ToolCallBuilder type(String type) {
            this.type = type;
            return this;
        }

        public ToolCallBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ToolCallBuilder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public ToolCallBuilder index(Integer index) {
            this.index = index;
            return this;
        }

        public ToolCall build() {
            ToolCall call = new ToolCall();
            call.setId(id);
            call.setType(type);
            call.setName(name);
            call.setArguments(arguments);
            call.setIndex(index);
            return call;
        }
    }
}
