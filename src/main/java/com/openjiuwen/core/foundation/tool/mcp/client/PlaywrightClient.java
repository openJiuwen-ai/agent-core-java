/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Playwright MCP client that delegates to SSE or stdio depending on the configured server path.
 * 
 * @since 0.1.7
 */
public class PlaywrightClient implements McpClient {
    private final McpClient delegate;

    /**
     * PlaywrightClient.
     * 
     * @param config config
     * @since 0.1.7
     */
    public PlaywrightClient(McpServerConfig config) {
        if (config.getServerPath() != null && config.getServerPath().startsWith("http")) {
            this.delegate = new SseClient(config);
        } else {
            this.delegate = new StdioClient(config);
        }
    }

    /**
     * connect.
     * 
     * @param retryTimes retryTimes
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        return delegate.connect(retryTimes, timeout);
    }

    /**
     * disconnect.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean disconnect(float timeout) throws Exception {
        return delegate.disconnect(timeout);
    }

    /**
     * listTools.
     * 
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Object> listTools(float timeout) throws Exception {
        return delegate.listTools(timeout);
    }

    /**
     * callTool.
     * 
     * @param toolName toolName
     * @param arguments arguments
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        return delegate.callTool(toolName, arguments, timeout);
    }

    /**
     * getToolInfo.
     * 
     * @param toolName toolName
     * @param timeout timeout
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        return delegate.getToolInfo(toolName, timeout);
    }

    /**
     * getServerPath.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getServerPath() {
        return delegate.getServerPath();
    }
}
