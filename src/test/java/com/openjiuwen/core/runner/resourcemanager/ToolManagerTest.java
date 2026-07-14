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

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python tests for
 * {@code openjiuwen/core/runner/resources_manager/tool_manager.py}.
 *
 * <p>Mirrors Python's {@code test_tool_manager_mcp_dedup} in
 * {@code tests/unit_tests/core/runner/test_tool_manager_mcp_dedup.py}.</p>
 */
class ToolManagerTest {

    @Test
    void defaultCreateClientRegistersMcpFactoriesAndNormalizesClientType() throws Exception {
        McpServerConfig config = McpServerConfig.builder()
                .serverId("srv-registry-default")
                .serverName("registry-default")
                .serverPath("http://127.0.0.1:3001/sse")
                .clientType("mcp_SSE")
                .build();

        Method method = ToolManager.class.getDeclaredMethod("defaultCreateClient", McpServerConfig.class);
        method.setAccessible(true);

        Object client = method.invoke(null, config);

        assertInstanceOf(com.openjiuwen.core.foundation.tool.mcp.client.SseClient.class, client);
        assertEquals("mcp_SSE", config.getClientType(), "lookup normalization must not mutate user config");
    }

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
    void addToolServerSerializesConcurrentCallsForSameServerId() throws Exception {
        CountDownLatch connectStarted = new CountDownLatch(1);
        CountDownLatch releaseConnect = new CountDownLatch(1);
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("gamma", "race-srv", "demo")),
                connectStarted, releaseConnect);
        ToolManager manager = new ToolManager(config -> client);
        McpServerConfig config = serverConfig("race-srv", "demo");
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<List<McpToolCard>> first = executor.submit(() ->
                    manager.addToolServer(config).toCompletableFuture().join());
            assertTrue(connectStarted.await(1, TimeUnit.SECONDS));
            Future<List<McpToolCard>> second = executor.submit(() ->
                    manager.addToolServer(config).toCompletableFuture().join());

            TimeUnit.MILLISECONDS.sleep(50);
            assertFalse(second.isDone());
            releaseConnect.countDown();

            assertEquals(List.of("gamma"), first.get(1, TimeUnit.SECONDS).stream().map(McpToolCard::getName).toList());
            assertEquals(List.of("gamma"), second.get(1, TimeUnit.SECONDS).stream().map(McpToolCard::getName).toList());
            assertEquals(1, client.connectCount);
            assertEquals(1, client.listToolsCount);
        } finally {
            releaseConnect.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void addToolServerRegistersDistinctServerIdsIndependently() {
        FakeMcpClient clientA = new FakeMcpClient(List.of(mcpCard("x", "srv-a", "alpha-srv")));
        FakeMcpClient clientB = new FakeMcpClient(List.of(mcpCard("y", "srv-b", "beta-srv")));
        ToolManager manager = new ToolManager(config ->
                "srv-a".equals(config.getServerId()) ? clientA : clientB);

        List<McpToolCard> cardsA = manager.addToolServer(serverConfig("srv-a", "alpha-srv"))
                .toCompletableFuture().join();
        List<McpToolCard> cardsB = manager.addToolServer(serverConfig("srv-b", "beta-srv"))
                .toCompletableFuture().join();

        assertEquals(List.of("x"), cardsA.stream().map(McpToolCard::getName).toList());
        assertEquals(List.of("y"), cardsB.stream().map(McpToolCard::getName).toList());
        assertEquals(1, clientA.connectCount);
        assertEquals(1, clientB.connectCount);
        assertEquals(List.of("srv-a"), manager.getMcpServerIds("alpha-srv"));
        assertEquals(List.of("srv-b"), manager.getMcpServerIds("beta-srv"));
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
    void addToolServerDisconnectsClientWhenToolRefreshFailsAfterConnect() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search")));
        client.listToolsFailure = new IllegalStateException("tools/list failed");
        ToolManager manager = new ToolManager(config -> client);

        BaseError error = assertThrows(BaseError.class,
                () -> manager.addToolServer(serverConfig()).toCompletableFuture().join());

        assertEquals(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, error.getStatus());
        assertEquals(1, client.connectCount);
        assertEquals(1, client.disconnectCount);
        assertTrue(manager.getMcpServerIds("demo").isEmpty());
        assertTrue(manager.getMcpToolIds("srv-1").isEmpty());
        assertNull(manager.getMcpTool("search", "srv-1", null));
    }

    @Test
    void addToolServerRollsBackPartiallyRegisteredToolsWhenRegistrationFails() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search"), mcpCard("search")));
        ToolManager manager = new ToolManager(config -> client);

        BaseError error = assertThrows(BaseError.class,
                () -> manager.addToolServer(serverConfig()).toCompletableFuture().join());

        assertEquals(StatusCode.RESOURCE_MCP_SERVER_ADD_ERROR, error.getStatus());
        assertEquals(1, client.disconnectCount);
        assertNull(manager.getTool("srv-1.demo.search"));
        assertTrue(manager.getMcpServerIds("demo").isEmpty());
        assertTrue(manager.getMcpToolIds("srv-1").isEmpty());
    }

    @Test
    void addToolServerUsesPositiveOperationTimeoutForMcpLifecycleCalls() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search")));
        ToolManager manager = new ToolManager(config -> client);
        McpServerConfig config = serverConfig();

        manager.addToolServer(config).toCompletableFuture().join();
        assertDoesNotThrow(() -> manager.getMcpTool("search", "srv-1", null).invoke(Map.of()));
        manager.removeToolServer("srv-1").toCompletableFuture().join();

        assertTrue(client.lastConnectTimeout > 0);
        assertTrue(client.lastListToolsTimeout > 0);
        assertTrue(client.lastCallToolTimeout > 0);
        assertTrue(client.lastDisconnectTimeout > 0);
    }

    @Test
    void addToolServerUsesConfiguredOperationTimeoutWhenProvided() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search")));
        ToolManager manager = new ToolManager(config -> client);
        McpServerConfig config = new McpServerConfig("srv-timeout", "demo", "http://localhost/mcp", "fake",
                Map.of("operation_timeout", 2.5D), Map.of(), Map.of());

        manager.addToolServer(config).toCompletableFuture().join();
        assertDoesNotThrow(() -> manager.getMcpTool("search", "srv-timeout", null).invoke(Map.of()));
        manager.removeToolServer("srv-timeout").toCompletableFuture().join();

        assertEquals(2.5F, client.lastConnectTimeout);
        assertEquals(2.5F, client.lastListToolsTimeout);
        assertEquals(2.5F, client.lastCallToolTimeout);
        assertEquals(2.5F, client.lastDisconnectTimeout);
    }

    @Test
    void refreshToolServerReplacesExistingMcpToolsWhenForced() {
        FakeMcpClient client = new FakeMcpClient(List.of(mcpCard("search")));
        ToolManager manager = new ToolManager(config -> client);
        manager.addToolServer(serverConfig()).toCompletableFuture().join();
        client.replaceCards(List.of(mcpCard("search"), mcpCard("lookup")));

        List<McpToolCard> refreshed = manager.refreshToolServer("srv-1", false, true).toCompletableFuture().join();

        assertEquals(List.of("search", "lookup"), refreshed.stream().map(McpToolCard::getName).toList());
        assertEquals(List.of("srv-1.demo.search", "srv-1.demo.lookup"), manager.getMcpToolIds("srv-1"));
        assertInstanceOf(McpTool.class, manager.getMcpTool("search", "srv-1", null));
        assertInstanceOf(McpTool.class, manager.getMcpTool("lookup", "srv-1", null));
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
        return serverConfig("srv-1", "demo");
    }

    private static McpServerConfig serverConfig(String serverId, String serverName) {
        return McpServerConfig.builder()
                .serverId(serverId)
                .serverName(serverName)
                .serverPath("http://localhost/mcp")
                .clientType("fake")
                .build();
    }

    private static McpToolCard mcpCard(String name) {
        return mcpCard(name, "srv-1", "demo");
    }

    private static McpToolCard mcpCard(String name, String serverId, String serverName) {
        return McpToolCard.builder()
                .name(name)
                .description("Tool " + name)
                .serverName(serverName)
                .serverId(serverId)
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
    public static final class FakeMcpClient implements McpClient {
        private List<Object> cards;
        private final CountDownLatch connectStarted;
        private final CountDownLatch releaseConnect;
        private int connectCount;
        private int disconnectCount;
        private int listToolsCount;
        private float lastConnectTimeout = McpServerConfig.NO_TIMEOUT;
        private float lastDisconnectTimeout = McpServerConfig.NO_TIMEOUT;
        private float lastListToolsTimeout = McpServerConfig.NO_TIMEOUT;
        private float lastCallToolTimeout = McpServerConfig.NO_TIMEOUT;
        private RuntimeException listToolsFailure;

        private FakeMcpClient(List<McpToolCard> cards) {
            this(cards, null, null);
        }

        private FakeMcpClient(List<McpToolCard> cards,
                              CountDownLatch connectStarted,
                              CountDownLatch releaseConnect) {
            this.cards = List.copyOf(cards);
            this.connectStarted = connectStarted;
            this.releaseConnect = releaseConnect;
        }

        private void replaceCards(List<McpToolCard> cards) {
            this.cards = List.copyOf(cards);
        }

        @Override
        public boolean connect(int retryTimes, float timeout) throws Exception {
            connectCount++;
            lastConnectTimeout = timeout;
            if (connectStarted != null && releaseConnect != null) {
                connectStarted.countDown();
                if (!releaseConnect.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("connect was not released");
                }
            }
            return true;
        }

        @Override
        public boolean disconnect(float timeout) {
            disconnectCount++;
            lastDisconnectTimeout = timeout;
            return true;
        }

        @Override
        public List<Object> listTools(float timeout) {
            listToolsCount++;
            lastListToolsTimeout = timeout;
            if (listToolsFailure != null) {
                throw listToolsFailure;
            }
            return cards;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            lastCallToolTimeout = timeout;
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
