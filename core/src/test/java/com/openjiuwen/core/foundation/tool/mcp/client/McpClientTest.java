/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code McpClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/mcp_client.py}.
 */
class McpClientTest {

    @Test
    void constructorCopiesServerPathAndMetadataKeepsMcpType() {
        FakeMcpClient client = new FakeMcpClient(new McpServerConfig("server", "stdio://server"));

        assertEquals("mcp", McpClient.CLIENT_TYPE);
        assertEquals("mcp", McpClient.getClientType());
        assertEquals("stdio://server", client.getServerPath());
        assertNull(client.getMetadata().get("client_name"));
        assertEquals("mcp", client.getMetadata().get("client_type"));
    }

    @Test
    void defaultOverloadsMatchPythonKeywordDefaults() {
        FakeMcpClient client = new FakeMcpClient(new McpServerConfig("server", "stdio://server"));

        assertTrue(client.connect().join());
        assertEquals("connect:1:-1.0", client.calls.get(0));
        assertEquals(List.of("tool-a"), client.listTools().join());
        assertEquals("listTools:-1.0", client.calls.get(1));
        assertEquals("called:demo:1", client.callTool("demo", Map.of("x", 1)).join());
        assertEquals("callTool:demo:{x=1}:-1.0", client.calls.get(2));
        assertEquals(Optional.of("info:demo"), client.getToolInfo("demo").join());
        assertEquals("getToolInfo:demo:-1.0", client.calls.get(3));
        assertEquals(List.of("resource-a"), client.listResources().join());
        assertEquals("listResources:-1.0", client.calls.get(4));
        assertEquals("body:uri://a", client.readResource("uri://a").join());
        assertEquals("readResource:uri://a:-1.0", client.calls.get(5));
    }

    @Test
    void closeDelegatesToDisconnectWithNoTimeout() {
        FakeMcpClient client = new FakeMcpClient(new McpServerConfig("server", "stdio://server"));

        assertTrue(client.close().join());
        assertEquals(List.of("disconnect:-1.0"), client.calls);
    }

    /**
     * Mirrors Python's {@code McpClient} test subclass in
     * {@code openjiuwen/core/foundation/tool/mcp/client/mcp_client.py}.
     */
    private static final class FakeMcpClient extends McpClient {

        private final List<String> calls = new ArrayList<>();

        private FakeMcpClient(McpServerConfig config) {
            super(config);
        }

        @Override
        public CompletableFuture<Boolean> connect(int retryTimes, double timeout) {
            calls.add("connect:" + retryTimes + ":" + timeout);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<Boolean> disconnect(double timeout) {
            calls.add("disconnect:" + timeout);
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture<List<Object>> listTools(double timeout) {
            calls.add("listTools:" + timeout);
            return CompletableFuture.completedFuture(List.of("tool-a"));
        }

        @Override
        public CompletableFuture<Object> callTool(String toolName, Map<String, Object> arguments, double timeout) {
            calls.add("callTool:" + toolName + ":" + arguments + ":" + timeout);
            return CompletableFuture.completedFuture("called:" + toolName + ":" + arguments.size());
        }

        @Override
        public CompletableFuture<Optional<Object>> getToolInfo(String toolName, double timeout) {
            calls.add("getToolInfo:" + toolName + ":" + timeout);
            return CompletableFuture.completedFuture(Optional.of("info:" + toolName));
        }

        @Override
        public CompletableFuture<List<Object>> listResources(double timeout) {
            calls.add("listResources:" + timeout);
            return CompletableFuture.completedFuture(List.of("resource-a"));
        }

        @Override
        public CompletableFuture<Object> readResource(String uri, double timeout) {
            calls.add("readResource:" + uri + ":" + timeout);
            return CompletableFuture.completedFuture("body:" + uri);
        }
    }
}
