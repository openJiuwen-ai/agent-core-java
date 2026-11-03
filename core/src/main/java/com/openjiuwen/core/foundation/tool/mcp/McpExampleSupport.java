/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.List;
import java.util.Map;

/**
 * Shared MCP example builders used by the Java example set.
 * 
 * @since 0.1.7
 */
public final class McpExampleSupport {
    /**
     * McpExampleSupport.
     * 
     * @since 0.1.7
     */
    private McpExampleSupport() {
    }

    /**
     * streamableHttpConfig.
     * 
     * @param serverName serverName
     * @param host host
     * @param port port
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static McpServerConfig streamableHttpConfig(String serverName, String host, int port, String path) {
        return McpServerConfig.builder().serverName(serverName).serverPath("http://" + host + ":" + port + "/" + path)
                .clientType("streamable-http").build();
    }

    /**
     * sseConfig.
     * 
     * @param serverName serverName
     * @param url url
     * @return the result
     * @since 0.1.7
     */
    public static McpServerConfig sseConfig(String serverName, String url) {
        return McpServerConfig.builder().serverName(serverName).serverPath(url).clientType("sse").build();
    }

    /**
     * stdioConfig.
     * 
     * @param serverName serverName
     * @param cwd cwd
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    public static McpServerConfig stdioConfig(String serverName, String cwd, List<String> args) {
        return McpServerConfig.builder().serverName(serverName).serverPath("stdio://" + serverName).clientType("stdio")
                .params(Map.of("cwd", cwd, "args", args)).build();
    }

    /**
     * openApiConfig.
     * 
     * @param serverName serverName
     * @param url url
     * @return the result
     * @since 0.1.7
     */
    public static McpServerConfig openApiConfig(String serverName, String url) {
        return McpServerConfig.builder().serverName(serverName).serverPath(url).clientType("openapi").build();
    }

    /**
     * playwrightConfig.
     * 
     * @param serverName serverName
     * @param url url
     * @return the result
     * @since 0.1.7
     */
    public static McpServerConfig playwrightConfig(String serverName, String url) {
        return McpServerConfig.builder().serverName(serverName).serverPath(url).clientType("playwright").build();
    }

    /**
     * describe.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    public static String describe(McpServerConfig config) {
        return "server=" + config.getServerName() + ", clientType=" + config.getClientType() + ", path="
                + config.getServerPath();
    }
}
