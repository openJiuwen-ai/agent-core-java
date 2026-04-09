/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Playwright MCP client that delegates to SSE or stdio depending on the configured server path.
 */
public class PlaywrightClient implements McpClient {

    private final McpClient delegate;

    public PlaywrightClient(McpServerConfig config) {
        if (config.getServerPath() != null && config.getServerPath().startsWith("http")) {
            this.delegate = new SseClient(config);
        } else {
            this.delegate = new StdioClient(config);
        }
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

    @Override
    public String getServerPath() {
        return delegate.getServerPath();
    }
}
