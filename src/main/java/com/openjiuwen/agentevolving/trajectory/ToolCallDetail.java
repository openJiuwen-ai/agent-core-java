/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.trajectory.types.ToolCallDetail.
 * 
 * @since 0.1.7
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
     * ToolCallDetail.
     * 
     * @since 0.1.7
     */
    public ToolCallDetail() {
    }

    /**
     * ToolCallDetail.
     * 
     * @param toolName toolName
     * @param callArgs callArgs
     * @param callResult callResult
     * @param toolDescription toolDescription
     * @param toolSchema toolSchema
     * @param toolCallId toolCallId
     * @since 0.1.7
     */
    public ToolCallDetail(String toolName, Object callArgs, Object callResult, String toolDescription,
            Map<String, Object> toolSchema, String toolCallId) {
        this.toolName = toolName;
        this.callArgs = callArgs;
        this.callResult = callResult;
        this.toolDescription = toolDescription;
        this.toolSchema = toolSchema != null ? new LinkedHashMap<>(toolSchema) : null;
        this.toolCallId = toolCallId;
    }

    /**
     * getToolName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getToolName() {
        return toolName;
    }

    /**
     * setToolName.
     * 
     * @param toolName toolName
     * @since 0.1.7
     */
    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * getCallArgs.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getCallArgs() {
        return callArgs;
    }

    /**
     * setCallArgs.
     * 
     * @param callArgs callArgs
     * @since 0.1.7
     */
    public void setCallArgs(Object callArgs) {
        this.callArgs = callArgs;
    }

    /**
     * getCallResult.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getCallResult() {
        return callResult;
    }

    /**
     * setCallResult.
     * 
     * @param callResult callResult
     * @since 0.1.7
     */
    public void setCallResult(Object callResult) {
        this.callResult = callResult;
    }

    /**
     * getToolDescription.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getToolDescription() {
        return toolDescription;
    }

    /**
     * setToolDescription.
     * 
     * @param toolDescription toolDescription
     * @since 0.1.7
     */
    public void setToolDescription(String toolDescription) {
        this.toolDescription = toolDescription;
    }

    /**
     * getToolSchema.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getToolSchema() {
        return toolSchema;
    }

    /**
     * setToolSchema.
     * 
     * @param toolSchema toolSchema
     * @since 0.1.7
     */
    public void setToolSchema(Map<String, Object> toolSchema) {
        this.toolSchema = toolSchema != null ? new LinkedHashMap<>(toolSchema) : null;
    }

    /**
     * getToolCallId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * setToolCallId.
     * 
     * @param toolCallId toolCallId
     * @since 0.1.7
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
