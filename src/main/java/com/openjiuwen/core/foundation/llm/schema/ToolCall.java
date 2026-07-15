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
 * @since 0.1.7
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
     * ToolCall.
     * 
     * @since 0.1.7
     */
    public ToolCall() {
    }

    /**
     * ToolCall.
     * 
     * @param id id
     * @param type type
     * @param name name
     * @param arguments arguments
     * @param index index
     * @since 0.1.7
     */
    public ToolCall(String id, String type, String name, String arguments, Integer index) {
        this.id = id;
        this.type = type != null ? type : "function";
        this.name = name;
        this.arguments = arguments;
        this.index = index;
    }

    /**
     * getId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getId() {
        return id;
    }

    /**
     * setId.
     * 
     * @param id id
     * @since 0.1.7
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getType() {
        return type;
    }

    /**
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * setName.
     * 
     * @param name name
     * @since 0.1.7
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * getArguments.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getArguments() {
        return arguments;
    }

    /**
     * setArguments.
     * 
     * @param arguments arguments
     * @since 0.1.7
     */
    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    /**
     * getIndex.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Integer getIndex() {
        return index;
    }

    /**
     * setIndex.
     * 
     * @param index index
     * @since 0.1.7
     */
    public void setIndex(Integer index) {
        this.index = index;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder {
        private String id;
        private String type = "function";
        private String name;
        private String arguments;
        private Integer index;

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * type.
         * 
         * @param type type
         * @return the result
         * @since 0.1.7
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * arguments.
         * 
         * @param arguments arguments
         * @return the result
         * @since 0.1.7
         */
        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * index.
         * 
         * @param index index
         * @return the result
         * @since 0.1.7
         */
        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public ToolCall build() {
            return new ToolCall(id, type, name, arguments, index);
        }
    }
}
