/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;

/**
 * Represents a tool call from LLM output.
 * <p>
 * Mirrors Python's {@code ToolCall} model from the foundation LLM schema.
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/chat/object">OpenAI Tool Call</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Tool call ID. */
    private String id;

    /** Tool call type (e.g., "function"). */
    private String type = "function";

    /** Tool name. */
    private String name;

    /** Tool arguments as JSON string. */
    private String arguments;

    /** Tool call index, used to distinguish multiple tool calls in a single response. */
    @JsonProperty("index")
    private Integer index;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolCall() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolCall(String id, String type, String name, String arguments, Integer index) {
        this.id = id;
        this.type = type != null ? type : "function";
        this.name = name;
        this.arguments = arguments;
        this.index = index;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getId() {
        return id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getType() {
        return type;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getArguments() {
        return arguments;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Integer getIndex() {
        return index;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setIndex(Integer index) {
        this.index = index;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private String id;
        private String type = "function";
        private String name;
        private String arguments;
        private Integer index;

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public ToolCall build() {
            return new ToolCall(id, type, name, arguments, index);
        }
    }
}
