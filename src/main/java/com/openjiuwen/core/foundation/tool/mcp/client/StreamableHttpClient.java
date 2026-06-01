/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.sdk.OfficialMcpClientFactory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Streamable HTTP transport based MCP client.
 * <p>
 * Mirrors Python's {@code StreamableHttpClient} in
 * {@code openjiuwen.core.foundation.tool.mcp.client.streamable_http_client}.
 * </p>
 */
public class StreamableHttpClient implements McpClient {

    private static final String CLIENT_NAME = "streamable_http";

    private final McpServerConfig config;
    private final McpClient delegate;
    private final String name;

    public StreamableHttpClient(McpServerConfig config) {
        this.config = normalizeConfig(config, null, null, null);
        this.delegate = OfficialMcpClientFactory.create(this.config);
        this.name = this.config.getServerName() != null ? this.config.getServerName() : CLIENT_NAME;
    }

    StreamableHttpClient(McpServerConfig config, McpClient delegate) {
        this.config = normalizeConfig(config, null, null, null);
        this.delegate = delegate;
        this.name = this.config.getServerName() != null ? this.config.getServerName() : CLIENT_NAME;
    }

    public StreamableHttpClient(String url, String name, Map<String, String> authHeaders,
                                Map<String, String> authQueryParams) {
        this.config = normalizeConfig(url, name, authHeaders, authQueryParams);
        this.delegate = OfficialMcpClientFactory.create(this.config);
        this.name = this.config.getServerName();
    }

    public StreamableHttpClient(String url) {
        this(url, null, null, null);
    }

    private McpServerConfig normalizeConfig(Object rawConfig, String name, Map<String, String> authHeaders,
                                            Map<String, String> authQueryParams) {
        if (rawConfig instanceof McpServerConfig mcpConfig) {
            mcpConfig.setClientType("streamable-http");
            return mcpConfig;
        }
        if (rawConfig instanceof String url) {
            String resolvedName = name != null && !name.isBlank() ? name : CLIENT_NAME;
            McpServerConfig newConfig = new McpServerConfig();
            newConfig.setServerId(resolvedName);
            newConfig.setServerName(resolvedName);
            newConfig.setServerPath(url);
            newConfig.setClientType("streamable-http");
            newConfig.setAuthHeaders(authHeaders != null ? authHeaders : Map.of());
            newConfig.setAuthQueryParams(authQueryParams != null ? authQueryParams : Map.of());
            return newConfig;
        }
        throw new IllegalArgumentException("Config must be McpServerConfig or String URL");
    }

    @Override
    public boolean connect(int retryTimes, float timeout) {
        try {
            float actualTimeout = timeout == McpServerConfig.NO_TIMEOUT ? 60.0f : timeout;
            return delegate.connect(retryTimes, actualTimeout);
        } catch (Exception e) {
            try {
                delegate.disconnect(McpServerConfig.NO_TIMEOUT);
            } catch (Exception ignored) {
                // Keep the connect result aligned with Python: connection failures return false.
            }
            return false;
        }
    }

    @Override
    public boolean disconnect(float timeout) {
        try {
            return delegate.disconnect(timeout);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        return delegate.listTools(timeout);
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        return extractToolResultContent(delegate.callTool(toolName, arguments, timeout));
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        return delegate.getToolInfo(toolName, timeout);
    }

    @Override
    public String getServerPath() {
        return delegate.getServerPath();
    }

    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    static Object extractToolResultContent(Object toolResult) {
        if (!(toolResult instanceof Map<?, ?> resultMap)) {
            return toolResult;
        }
        Object rawContent = resultMap.get("content");
        if (!(rawContent instanceof List<?> content) || content.isEmpty()) {
            Object text = resultMap.get("text");
            return text != null ? text : toolResult;
        }

        Object item = content.get(content.size() - 1);
        Map<String, Object> itemMap = toStringMap(item);
        if (itemMap.isEmpty()) {
            return item != null ? item.toString() : null;
        }
        Object text = itemMap.get("text");
        if (text != null) {
            return text;
        }

        Object mimeType = itemMap.getOrDefault("mimeType", itemMap.get("mime_type"));
        Object data = itemMap.get("data");
        if (data != null) {
            if (mimeType != null && String.valueOf(mimeType).startsWith("image/")) {
                return "[image content: " + mimeType + ", " + String.valueOf(data).length() + " base64 chars]";
            }
            return data;
        }
        if (item instanceof Map<?, ?>) {
            Map<String, Object> dumped = new LinkedHashMap<>(itemMap);
            dumped.remove("data");
            return Collections.unmodifiableMap(dumped);
        }
        return item.toString();
    }

    private static Map<String, Object> toStringMap(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
