/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
 */
class ToolManagerTest {

    @Test
    void addGetAndRemoveLocalTool() {
        ToolManager manager = new ToolManager();
        Tool tool = new EchoTool("tool-1");

        manager.addTool("tool-1", tool);

        assertSame(tool, manager.getTool("tool-1"));
        assertThrows(IllegalArgumentException.class, () -> manager.addTool("tool-1", tool));
        assertSame(tool, manager.removeTool("tool-1"));
        assertNull(manager.getTool("tool-1"));
    }

    @Test
    void addToolServerRegistersMcpToolsAndCachesDuplicateServerAdd() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search"), mcpCard("lookup")));
        ToolManager manager = new ToolManager(config -> client);
        McpServerConfig config = serverConfig();

        List<McpToolCard> first = manager.addToolServer(config, 60.0D).toCompletableFuture().join();
        List<McpToolCard> second = manager.addToolServer(config, 60.0D).toCompletableFuture().join();

        assertEquals(1, client.connectCount);
        assertEquals(List.of("srv-1.demo.search", "srv-1.demo.lookup"),
                first.stream().map(McpToolCard::getId).toList());
        assertEquals(first.stream().map(McpToolCard::getId).toList(),
                second.stream().map(McpToolCard::getId).toList());
        assertEquals(List.of("srv-1"), manager.getMcpServerIds("demo"));
        assertEquals(List.of("srv-1.demo.search", "srv-1.demo.lookup"), manager.getMcpToolIds("srv-1"));
        assertEquals("srv-1.demo.search", manager.getMcpToolId("srv-1", "search"));
        assertInstanceOf(List.class, manager.getMcpToolId("srv-1"));
        assertInstanceOf(McpTool.class, manager.getMcpTool("search", "srv-1", null));
        assertEquals(2, manager.getMcpTools("srv-1", null).size());
    }

    @Test
    void removeToolServerDisconnectsClientAndRemovesNameIndex() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search")));
        ToolManager manager = new ToolManager(config -> client);
        McpServerConfig config = serverConfig();
        manager.addToolServer(config).toCompletableFuture().join();

        List<String> removed = manager.removeToolServer("srv-1", true).toCompletableFuture().join();

        assertEquals(List.of("srv-1.demo.search"), removed);
        assertEquals(1, client.disconnectCount);
        assertTrue(manager.getMcpServerIds("demo").isEmpty());
        assertTrue(manager.getMcpToolIds("srv-1").isEmpty());
        assertNull(manager.getMcpTool("search", "srv-1", null));
    }

    @Test
    void missingToolServerErrorStatusMirrorsPython() {
        ToolManager manager = new ToolManager(config -> new FakeMcpClient(List.of()));

        BaseError removeError = assertThrows(BaseError.class,
                () -> manager.removeToolServer("missing", false));
        assertEquals(StatusCode.RESOURCE_MCP_SERVER_REMOVE_ERROR, removeError.getStatus());

        BaseError refreshError = assertThrows(BaseError.class,
                () -> manager.refreshToolServer("missing", false, false));
        assertEquals(StatusCode.RESOURCE_MCP_SERVER_REFRESH_ERROR, refreshError.getStatus());

        assertTrue(manager.removeToolServer("missing", true).toCompletableFuture().join().isEmpty());
        assertTrue(manager.refreshToolServer("missing", true, false).toCompletableFuture().join().isEmpty());
    }

    @Test
    void sysOperationToolResourcesReturnCopies() {
        ToolManager manager = new ToolManager();

        manager.addSysOperationTools("sys-1", List.of("tool-a", "tool-b"));

        List<String> ids = manager.getSysOperationToolIds("sys-1");
        assertEquals(List.of("tool-a", "tool-b"), ids);
        assertThrows(UnsupportedOperationException.class, () -> ids.add("mutated"));
        assertEquals(List.of("tool-a", "tool-b"), manager.removeSysOperationTools("sys-1"));
        assertTrue(manager.getSysOperationToolIds("sys-1").isEmpty());
    }

    private static McpServerConfig serverConfig() {
        return McpServerConfig.builder()
                .serverId("srv-1")
                .serverName("demo")
                .serverPath("http://localhost/mcp")
                .clientType("fake")
                .build();
    }

    private static McpToolCard mcpCard(String name) {
        return McpToolCard.builder()
                .name(name)
                .description("Tool " + name)
                .serverName("demo")
                .serverId("srv-1")
                .inputParams(Map.of())
                .build();
    }

    /**
     * Mirrors Python's test-local tool double in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
     */
    private static final class EchoTool extends Tool {
        private EchoTool(String id) {
            super(ToolCard.builder().id(id).name("echo").description("echo").build());
        }
    }

    /**
     * Mirrors Python's MCP client protocol collaborator in
     * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
     */
    private static final class FakeMcpClient implements McpClient {
        private final List<Object> cards;
        private int connectCount;
        private int disconnectCount;

        private FakeMcpClient(List<McpToolCard> cards) {
            this.cards = List.copyOf(cards);
        }

        @Override
        public boolean connect(int retryTimes, float timeout) {
            connectCount++;
            return true;
        }

        @Override
        public boolean disconnect(float timeout) {
            disconnectCount++;
            return true;
        }

        @Override
        public List<Object> listTools(float timeout) {
            return cards;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            return Map.of("tool", toolName, "arguments", arguments);
        }

        @Override
        public Optional<Object> getToolInfo(String toolName, float timeout) {
            return Optional.empty();
        }

        @Override
        public String getServerPath() {
            return "http://localhost/mcp";
        }
    }
}
