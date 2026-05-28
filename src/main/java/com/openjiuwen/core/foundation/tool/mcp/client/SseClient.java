/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.sdk.OfficialMcpClientFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SSE transport based MCP client.
 * <p>
 * Mirrors Python's {@code SseClient} in
 * {@code openjiuwen.core.foundation.tool.mcp.client.sse_client}.
 * </p>
 */
public class SseClient implements McpClient {

    private final McpServerConfig config;
    private final McpClient delegate;

    public SseClient(McpServerConfig config) {
        this.config = config;
        this.config.setClientType("sse");
        this.delegate = OfficialMcpClientFactory.create(this.config);
    }

    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        return delegate.connect(retryTimes, timeout);
    }

    @Override
    public boolean disconnect(float timeout) throws Exception {
        return delegate.disconnect(timeout);
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        return delegate.listTools(timeout);
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        return delegate.callTool(toolName, arguments, timeout);
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        return delegate.getToolInfo(toolName, timeout);
    }

    public List<Object> listResources(float timeout) {
        throw new UnsupportedOperationException("MCP resource listing is not exposed by the current SSE adapter");
    }

    public Object readResource(String uri, float timeout) {
        throw new UnsupportedOperationException("MCP resource reading is not exposed by the current SSE adapter");
    }

    @Override
    public String getServerPath() {
        return delegate.getServerPath();
    }
}
