/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ToolCallDetail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCallDetail {
    private String toolName;
    private Object callArgs;
    private Object callResult;
    private String toolDescription;
    private Map<String, Object> toolSchema;
    private String toolCallId;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolCallDetail() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolCallDetail(String toolName,
                          Object callArgs,
                          Object callResult,
                          String toolDescription,
                          Map<String, Object> toolSchema,
                          String toolCallId) {
        this.toolName = toolName;
        this.callArgs = callArgs;
        this.callResult = callResult;
        this.toolDescription = toolDescription;
        this.toolSchema = toolSchema != null ? new LinkedHashMap<>(toolSchema) : null;
        this.toolCallId = toolCallId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getCallArgs() {
        return callArgs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCallArgs(Object callArgs) {
        this.callArgs = callArgs;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getCallResult() {
        return callResult;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCallResult(Object callResult) {
        this.callResult = callResult;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getToolDescription() {
        return toolDescription;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setToolDescription(String toolDescription) {
        this.toolDescription = toolDescription;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getToolSchema() {
        return toolSchema;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setToolSchema(Map<String, Object> toolSchema) {
        this.toolSchema = toolSchema != null ? new LinkedHashMap<>(toolSchema) : null;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    /**
     * Create a new builder for ToolCallDetail.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ToolCallDetail.
     */
    public static final class Builder {
        private String toolName;
        private Object callArgs;
        private Object callResult;
        private String toolDescription;
        private Map<String, Object> toolSchema;
        private String toolCallId;

        private Builder() {
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder callArgs(Object callArgs) {
            this.callArgs = callArgs;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder callResult(Object callResult) {
            this.callResult = callResult;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder toolDescription(String toolDescription) {
            this.toolDescription = toolDescription;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder toolSchema(Map<String, Object> toolSchema) {
            this.toolSchema = toolSchema;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        /**
         * Build the ToolCallDetail instance.
         *
         * @return new ToolCallDetail
         */
        public ToolCallDetail build() {
            return new ToolCallDetail(toolName, callArgs, callResult, toolDescription, toolSchema, toolCallId);
        }
    }
}
