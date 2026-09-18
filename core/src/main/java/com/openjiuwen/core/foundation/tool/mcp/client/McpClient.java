/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.common.clients.BaseClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code McpClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/mcp_client.py}.
 */
public abstract class McpClient extends BaseClient {

    protected static final String __client_name__ = null;
    public static final String __client_type__ = "mcp";
    public static final String CLIENT_TYPE = __client_type__;

    private final String serverPath;

    protected McpClient(McpServerConfig config) {
        super();
        McpServerConfig nonNullConfig = Objects.requireNonNull(config, "config");
        this.serverPath = nonNullConfig.getServerPath();
    }

    public static String getClientType() {
        return CLIENT_TYPE;
    }

    public String getServerPath() {
        return serverPath;
    }

    public abstract CompletableFuture<Boolean> connect(int retryTimes, double timeout);

    public CompletableFuture<Boolean> connect() {
        return connect(1, McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<Boolean> disconnect(double timeout);

    public CompletableFuture<Boolean> disconnect() {
        return disconnect(McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<List<Object>> listTools(double timeout);

    public CompletableFuture<List<Object>> listTools() {
        return listTools(McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<Object> callTool(
            String toolName,
            Map<String, Object> arguments,
            double timeout);

    public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments) {
        return callTool(toolName, arguments, McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<Optional<Object>> getToolInfo(String toolName, double timeout);

    public CompletableFuture<Optional<Object>> getToolInfo(String toolName) {
        return getToolInfo(toolName, McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<List<Object>> listResources(double timeout);

    public CompletableFuture<List<Object>> listResources() {
        return listResources(McpServerConfig.NO_TIMEOUT);
    }

    public abstract CompletableFuture<Object> readResource(String uri, double timeout);

    public CompletableFuture<Object> readResource(String uri) {
        return readResource(uri, McpServerConfig.NO_TIMEOUT);
    }

    @Override
    public CompletableFuture<Boolean> close() {
        return disconnect();
    }

    @Override
    public Map<String, Object> getMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", null);
        metadata.put("client_type", CLIENT_TYPE);
        return metadata;
    }
}
