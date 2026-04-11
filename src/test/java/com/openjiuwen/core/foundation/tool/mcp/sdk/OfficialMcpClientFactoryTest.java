/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.resourcemanager.ToolMgr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialMcpClientFactoryTest {

    @Test
    @DisplayName("Main MCP client types are routed to official adapter")
    void routesMainClientTypesToOfficialAdapter() throws Exception {
        assertInstanceOf(OfficialSdkMcpClient.class, createClient("stdio"));
        assertInstanceOf(OfficialSdkMcpClient.class, createClient("sse"));
        assertInstanceOf(OfficialSdkMcpClient.class, createClient("streamable-http"));
        assertInstanceOf(OfficialSdkMcpClient.class, createClient("http"));
    }

    @Test
    @DisplayName("Mapper preserves stdio params and auth settings")
    void mapperPreservesParamsAndAuthSettings() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("stdio-server")
                .serverPath("npx")
                .clientType("stdio")
                .params(Map.of(
                        "command", "node",
                        "args", List.of("server.js", "--mode", "stdio"),
                        "env", Map.of("API_KEY", "demo"),
                        "cwd", "/tmp/mcp"
                ))
                .authHeaders(Map.of("Authorization", "Bearer demo"))
                .build();

        OfficialMcpClientFactory.OfficialTransportConfig mapped = OfficialMcpClientFactory.map(config);

        assertEquals(OfficialMcpClientFactory.OfficialTransportType.STDIO, mapped.transportType());
        assertEquals("node", mapped.command());
        assertEquals(List.of("server.js", "--mode", "stdio"), mapped.args());
        assertEquals(Map.of("API_KEY", "demo"), mapped.env());
        assertEquals("/tmp/mcp", mapped.cwd());
        assertEquals(Map.of("Authorization", "Bearer demo"), mapped.authHeaders());
    }

    @Test
    @DisplayName("Non-main MCP client types stay on legacy implementations")
    void nonMainClientTypesStayOnLegacyImplementations() throws Exception {
        assertFalse(OfficialMcpClientFactory.supports("openapi"));
        assertFalse(OfficialMcpClientFactory.supports("playwright"));
        assertEquals("com.openjiuwen.core.foundation.tool.mcp.client.OpenApiClient",
                createClient("openapi").getClass().getName());
        assertEquals("com.openjiuwen.core.foundation.tool.mcp.client.PlaywrightClient",
                createClient("playwright").getClass().getName());
    }

    private McpClient createClient(String clientType) throws Exception {
        ToolMgr toolMgr = new ToolMgr();
        Method createClient = ToolMgr.class.getDeclaredMethod("createClient", McpServerConfig.class);
        createClient.setAccessible(true);
        return (McpClient) createClient.invoke(toolMgr, McpServerConfig.builder()
                .serverName("test-server")
                .serverPath("http://localhost:8930/mcp")
                .clientType(clientType)
                .build());
    }
}
