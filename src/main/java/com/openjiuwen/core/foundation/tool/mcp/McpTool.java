/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;

import java.util.Iterator;
import java.util.Map;

/**
 * MCP Tool that wraps MCP server tools for LLM function calling.
 * <p>
 * Mirrors Python's {@code MCPTool} class.
 */
public class McpTool extends Tool {

    private final McpClient mcpClient;

    /**
     * Create an MCP tool.
     *
     * @param mcpClient the MCP client instance
     * @param card      the MCP tool card
     */
    public McpTool(McpClient mcpClient, McpToolCard card) {
        super(card);
        if (mcpClient == null) {
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_CLIENT_NOT_SUPPORTED,
                    "card", card.toString());
        }
        this.mcpClient = mcpClient;
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        try {
            Map<String, Object> arguments = inputs != null ? inputs : Map.of();
            Object result = mcpClient.callTool(card.getName(), arguments);
            return Map.of("result", result);
        } catch (Exception e) {
            throw ErrorHelper.buildError(StatusCode.TOOL_MCP_EXECUTION_ERROR,
                    "reason", e.getMessage(), "method", "invoke", "card", card.toString());
        }
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        throw ErrorHelper.buildError(StatusCode.TOOL_STREAM_NOT_SUPPORTED, "card", card.toString());
    }
}
