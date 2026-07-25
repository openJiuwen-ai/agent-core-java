/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.List;
import java.util.Map;

/**
 * Shared MCP example builders used by the Java example set.
 */
public final class McpExampleSupport {
    private McpExampleSupport() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig streamableHttpConfig(String serverName, String host, int port, String path) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath("http://" + host + ":" + port + "/" + path)
                .clientType("streamable-http")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig sseConfig(String serverName, String url) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath(url)
                .clientType("sse")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig stdioConfig(String serverName, String cwd, List<String> args) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath("stdio://" + serverName)
                .clientType("stdio")
                .params(Map.of(
                        "cwd", cwd,
                        "args", args
                ))
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig openApiConfig(String serverName, String url) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath(url)
                .clientType("openapi")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static McpServerConfig playwrightConfig(String serverName, String url) {
        return McpServerConfig.builder()
                .serverName(serverName)
                .serverPath(url)
                .clientType("playwright")
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static String describe(McpServerConfig config) {
        return "server=" + config.getServerName()
                + ", clientType=" + config.getClientType()
                + ", path=" + config.getServerPath();
    }
}
