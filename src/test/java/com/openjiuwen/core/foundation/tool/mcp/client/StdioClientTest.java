/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code StdioClient} behavior in
 * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.
 */
class StdioClientTest {

    @Test
    void buildServerParametersUsesParamsAndNormalizesEncodingHandler() {
        StdioClient client = new StdioClient(config(Map.of(
                "command", "python",
                "args", List.of("-m", "server"),
                "env", Map.of("A", "B"),
                "cwd", "workspace",
                "encoding_error_handler", "drop"
        )));

        StdioClient.StdioServerParameters parameters = client.buildServerParameters();

        assertEquals("python", parameters.command());
        assertEquals(List.of("-m", "server"), parameters.args());
        assertEquals(Map.of("A", "B"), parameters.env());
        assertEquals("workspace", parameters.cwd());
        assertEquals("strict", parameters.encodingErrorHandler());
    }

    @Test
    void connectInitializesSessionAndDisconnectIsIdempotent() throws Exception {
        FakeSession session = new FakeSession();
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> session);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertTrue(session.initialized);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(session.closed);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertEquals(1, session.closeCalls);
    }

    @Test
    void connectReturnsFalseWhenSessionCannotOpen() {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> {
                    throw new IllegalStateException("boom");
                });

        assertFalse(client.connect(1, McpServerConfig.NO_TIMEOUT));
    }

    @Test
    void operationsRequireConnectedSession() {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> new FakeSession());

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.listTools(McpServerConfig.NO_TIMEOUT));

        assertEquals("Not connected to Stdio server", error.getMessage());
    }

    @Test
    void listToolsMapsSessionToolsToMcpCards() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new StdioClient.ToolDefinition("lookup", "Lookup",
                Map.of("type", "object")));
        StdioClient client = connectedClient(session);

        List<Object> tools = client.listTools(McpServerConfig.NO_TIMEOUT);

        assertEquals(1, tools.size());
        McpToolCard card = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("lookup", card.getName());
        assertEquals("stdio-server", card.getServerName());
        assertEquals("srv-stdio", card.getServerId());
        assertEquals("Lookup", card.getDescription());
        assertEquals(Map.of("type", "object"), card.getInputParams());
    }

    @Test
    void callToolExtractsResultContentAndPassesArguments() throws Exception {
        FakeSession session = new FakeSession();
        session.callResult = Map.of("content", List.of(Map.of("text", "ok")));
        StdioClient client = connectedClient(session);

        Object result = client.callTool("lookup", Map.of("query", "hello"), McpServerConfig.NO_TIMEOUT);

        assertEquals("ok", result);
        assertEquals("lookup", session.lastToolName);
        assertEquals(Map.of("query", "hello"), session.lastArguments);
    }

    @Test
    void getToolInfoFindsMatchingToolOrReturnsEmpty() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new StdioClient.ToolDefinition("lookup", "Lookup", Map.of()));
        StdioClient client = connectedClient(session);

        assertTrue(client.getToolInfo("lookup", McpServerConfig.NO_TIMEOUT).isPresent());
        assertTrue(client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT).isEmpty());
    }

    @Test
    void resourcesAreDelegatedToSession() throws Exception {
        FakeSession session = new FakeSession();
        session.resources = List.of(Map.of("uri", "file://a"));
        session.contents = List.of(Map.of("text", "body"));
        StdioClient client = connectedClient(session);

        assertEquals(session.resources, client.listResources(McpServerConfig.NO_TIMEOUT));
        assertSame(session.contents, client.readResource("file://a", McpServerConfig.NO_TIMEOUT));
        assertEquals("file://a", session.lastResourceUri);
    }

    private static StdioClient connectedClient(FakeSession session) {
        StdioClient client = new StdioClient(config(Map.of("command", "server")),
                parameters -> session);
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        return client;
    }

    private static McpServerConfig config(Map<String, Object> params) {
        return new McpServerConfig("srv-stdio", "stdio-server", "ignored", "stdio",
                params, Map.of(), Map.of());
    }

    /**
     * Mirrors Python's fake {@code ClientSession} behavior needed by {@code StdioClient} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/stdio_client.py}.
     */
    private static final class FakeSession implements StdioClient.StdioSession {
        private boolean initialized;
        private boolean closed;
        private int closeCalls;
        private final List<StdioClient.ToolDefinition> tools = new ArrayList<>();
        private Object callResult = Map.of("content", List.of());
        private String lastToolName;
        private Map<String, Object> lastArguments = new LinkedHashMap<>();
        private List<Object> resources = List.of();
        private Object contents;
        private String lastResourceUri;

        @Override
        public void initialize(float timeout) {
            initialized = true;
        }

        @Override
        public List<StdioClient.ToolDefinition> listTools(float timeout) {
            return tools;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            lastToolName = toolName;
            lastArguments = new LinkedHashMap<>(arguments);
            return callResult;
        }

        @Override
        public List<Object> listResources(float timeout) {
            return resources;
        }

        @Override
        public Object readResource(String uri, float timeout) {
            lastResourceUri = uri;
            return contents;
        }

        @Override
        public void close(float timeout) {
            closed = true;
            closeCalls++;
        }
    }
}
