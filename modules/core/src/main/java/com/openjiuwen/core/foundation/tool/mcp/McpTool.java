/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.Iterator;
import java.util.Map;

/**
 * MCP Tool that wraps MCP server tools for LLM function calling.
 * <p>
 * Mirrors Python's {@code MCPTool} class.
 * 
 * @since 0.1.7
 */
public class McpTool extends Tool {
    private final McpClient mcpClient;

    /**
     * Create an MCP tool.
     * 
     * @param mcpClient the MCP client instance
     * @param card the MCP tool card
     * @since 0.1.7
     */
    public McpTool(McpClient mcpClient, McpToolCard card) {
        super(card);
        if (mcpClient == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_CLIENT_NOT_SUPPORTED, "card", card.toString());
        }
        this.mcpClient = mcpClient;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        try {
            Map<String, Object> arguments = inputs != null ? inputs : Map.of();
            // Schema validation: format inputs against inputParams if defined
            Map<String, Object> inputParams = card.getInputParams();
            if (inputParams != null && !inputParams.isEmpty()) {
                arguments = formatArguments(arguments, inputParams);
            }
            Object result = mcpClient.callTool(card.getName(), arguments);
            return Map.of("result", result);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_EXECUTION_ERROR, null, null, e,
                    Map.of("reason", reason, "method", "invoke", "card", card.toString()));
        }
    }

    /**
     * Formats MCP arguments while preserving Python's formatting-stage error for undeclared extra fields.
     *
     * @param arguments raw MCP arguments
     * @param inputParams MCP input schema
     * @return formatted MCP arguments
     * @since 0.1.14
     */
    private static Map<String, Object> formatArguments(Map<String, Object> arguments,
            Map<String, Object> inputParams) {
        try {
            return SchemaUtils.formatWithSchema(arguments, inputParams);
        } catch (ValidationError error) {
            if (!isImplicitAdditionalPropertyError(error, inputParams)) {
                throw error;
            }
            Throwable cause = error.getCause();
            throw new ValidationError(StatusCode.SCHEMA_FORMAT_INVALID, null, null, error,
                    Map.of("reason", cause.getMessage(), "data", String.valueOf(arguments)));
        }
    }

    /**
     * Checks whether validation rejected an extra field under an implicit additional-property policy.
     *
     * @param error schema validation error
     * @param inputParams MCP input schema
     * @return true when Python reports the failure during formatting
     * @since 0.1.14
     */
    private static boolean isImplicitAdditionalPropertyError(ValidationError error,
            Map<String, Object> inputParams) {
        if (inputParams.containsKey("additionalProperties")) {
            return false;
        }
        Throwable cause = error.getCause();
        return error.getCode() == StatusCode.SCHEMA_VALIDATE_INVALID.getCode()
                && cause instanceof IllegalArgumentException
                && cause.getMessage() != null
                && cause.getMessage().startsWith("Unexpected keyword argument:");
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", card.toString());
    }
}
