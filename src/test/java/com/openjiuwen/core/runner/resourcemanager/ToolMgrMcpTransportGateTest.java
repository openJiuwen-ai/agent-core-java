/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.mcp.sdk.support.LocalOfficialMcpHttpFixture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolMgrMcpTransportGateTest {

    @Test
    @DisplayName("addToolServer only persists ready server after tools list succeeds")
    void addToolServerOnlyPersistsReadyServerAfterToolsListSucceeds() throws Exception {
        ToolMgr toolMgr = new ToolMgr();
        try (LocalOfficialMcpHttpFixture fixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.SUCCESS)) {
            McpServerConfig config = baseHttpConfig("ready-server", fixture.streamableHttpUrl(), "streamable-http");

            List<McpToolCard> cards = toolMgr.addToolServer(config, null);

            assertEquals(1, cards.size());
            assertEquals(List.of(config.getServerId()), toolMgr.getMcpServerIds(config.getServerName()));
            assertTrue(getMcpServerResources(toolMgr).containsKey(config.getServerId()));
            assertTrue(getTools(toolMgr).containsKey(ToolMgr.generateMcpToolId(config.getServerId(), config.getServerName(), "fixture_http_tool")));
        }
    }

    @Test
    @DisplayName("initialize and LIST_TOOLS failures leave no fake ready resources behind")
    void initializeAndListToolsFailuresLeaveNoFakeReadyResourcesBehind() throws Exception {
        ToolMgr toolMgr = new ToolMgr();
        try (LocalOfficialMcpHttpFixture initializeFixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.FAIL_INITIALIZE)) {
            McpServerConfig initializeConfig = baseHttpConfig("initialize-server", initializeFixture.streamableHttpUrl(), "streamable-http");

            BaseError initializeError = assertThrows(BaseError.class, () -> toolMgr.addToolServer(initializeConfig, null));

            assertEquals(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, initializeError.getStatus());
            assertTrue(initializeError.getMessage().contains("INITIALIZE"));
            assertFalse(getMcpServerResources(toolMgr).containsKey(initializeConfig.getServerId()));
            assertTrue(toolMgr.getMcpServerIds(initializeConfig.getServerName()).isEmpty());
        }

        try (LocalOfficialMcpHttpFixture listFixture = LocalOfficialMcpHttpFixture.start(LocalOfficialMcpHttpFixture.Mode.FAIL_LIST_TOOLS)) {
            McpServerConfig listConfig = baseHttpConfig("list-server", listFixture.streamableHttpUrl(), "streamable-http");

            BaseError listError = assertThrows(BaseError.class, () -> toolMgr.addToolServer(listConfig, null));

            assertEquals(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, listError.getStatus());
            assertTrue(listError.getMessage().contains("LIST_TOOLS"));
            assertFalse(getMcpServerResources(toolMgr).containsKey(listConfig.getServerId()));
            assertTrue(toolMgr.getMcpServerIds(listConfig.getServerName()).isEmpty());
            assertFalse(getTools(toolMgr).containsKey(ToolMgr.generateMcpToolId(listConfig.getServerId(), listConfig.getServerName(), "fixture_http_tool")));
        }
    }

    @Test
    @DisplayName("CONNECT failures still expose stage diagnostics and leave no fake ready resources")
    void connectFailuresStillExposeStageDiagnosticsAndLeaveNoFakeReadyResources() throws Exception {
        ToolMgr toolMgr = new ToolMgr();
        McpServerConfig brokenConfig = baseHttpConfig("broken-server", "://bad-url", "streamable-http");

        BaseError error = assertThrows(BaseError.class, () -> toolMgr.addToolServer(brokenConfig, null));

        assertEquals(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, error.getStatus());
        assertTrue(error.getMessage().contains("CONNECT"));
        assertFalse(error.getMessage().toLowerCase().contains("schema validation"));
        assertTrue(toolMgr.getMcpServerIds(brokenConfig.getServerName()).isEmpty());
        assertFalse(getMcpServerResources(toolMgr).containsKey(brokenConfig.getServerId()));
    }

    private McpServerConfig baseHttpConfig(String serverId, String serverPath, String clientType) {
        return McpServerConfig.builder()
                .serverId(serverId)
                .serverName(serverId)
                .serverPath(serverPath)
                .clientType(clientType)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, ToolMgr.McpServerResource> getMcpServerResources(ToolMgr toolMgr) throws Exception {
        Field field = ToolMgr.class.getDeclaredField("mcpServerResources");
        field.setAccessible(true);
        return (Map<String, ToolMgr.McpServerResource>) field.get(toolMgr);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getTools(ToolMgr toolMgr) throws Exception {
        Field field = ToolMgr.class.getDeclaredField("tools");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(toolMgr);
    }
}
