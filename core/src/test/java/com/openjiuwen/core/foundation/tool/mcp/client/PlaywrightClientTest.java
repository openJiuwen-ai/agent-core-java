/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code PlaywrightClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/playwright_client.py}.
 */
class PlaywrightClientTest {

    @Test
    void connectWithHttpServerPathUsesSseAndMapsTools() throws Exception {
        McpServerConfig config = new McpServerConfig("browser", "https://mcp.example.test/sse");
        FakeSession session = new FakeSession();
        session.tools.add(new McpToolCard("tool-id", "browser_click", "Click", Map.of("type", "object"),
                "ignored", "ignored-server"));
        CapturingFactory factory = new CapturingFactory(session);

        PlaywrightClient client = new PlaywrightClient(config, factory);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertEquals(PlaywrightClient.TransportType.SSE, factory.transportType);
        assertEquals("https://mcp.example.test/sse", factory.serverPath);
        assertTrue(session.initialized);

        List<Object> tools = client.listTools(McpServerConfig.NO_TIMEOUT);
        McpToolCard card = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("browser_click", card.getName());
        assertEquals("browser", card.getServerName());
        assertEquals(config.getServerId(), card.getServerId());
        assertEquals(Map.of("type", "object"), card.getInputParams());
    }

    @Test
    void connectCanUseInjectedStdioServerParametersFromDynamicPythonBoundary() {
        ServerParameters parameters = ServerParameters.builder("npx").args("-y", "@playwright/mcp@latest").build();
        McpServerConfig config = McpServerConfig.builder()
                .serverName("browser")
                .serverPath("unused")
                .params(Map.of("stdio_server_parameters", parameters))
                .build();
        CapturingFactory factory = new CapturingFactory(new FakeSession());

        PlaywrightClient client = new PlaywrightClient(config, factory);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertEquals(PlaywrightClient.TransportType.STDIO, factory.transportType);
        assertEquals(parameters, factory.serverPath);
    }

    @Test
    void unsupportedServerPathReturnsFalseLikePythonConnectExceptionPath() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("browser")
                .serverPath("stdio://not-a-python-stdio-parameter")
                .build();
        CapturingFactory factory = new CapturingFactory(new FakeSession());

        PlaywrightClient client = new PlaywrightClient(config, factory);

        assertFalse(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertFalse(factory.opened);
    }

    @Test
    void listAndCallRequireConnectionAndCallExtractsLatestTextContent() throws Exception {
        PlaywrightClient unconnected = new PlaywrightClient(
                new McpServerConfig("browser", "https://mcp.example.test/sse"),
                new CapturingFactory(new FakeSession()));
        assertThrows(RuntimeException.class, () -> unconnected.listTools(McpServerConfig.NO_TIMEOUT));

        FakeSession session = new FakeSession();
        session.callResult = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("old"), new McpSchema.TextContent("latest")),
                false,
                null,
                null);
        PlaywrightClient connected = new PlaywrightClient(
                new McpServerConfig("browser", "https://mcp.example.test/sse"),
                new CapturingFactory(session));
        assertTrue(connected.connect(1, McpServerConfig.NO_TIMEOUT));

        assertEquals("latest", connected.callTool("browser_snapshot", Map.of("compact", true),
                McpServerConfig.NO_TIMEOUT));
        assertEquals("browser_snapshot", session.calledToolName);
        assertEquals(Map.of("compact", true), session.calledArguments);
    }

    @Test
    void getToolInfoReturnsMatchingToolAndDisconnectIsIdempotent() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new McpToolCard(null, "browser_navigate", "Navigate", Map.of(),
                "browser", "server-id"));
        PlaywrightClient client = new PlaywrightClient(
                new McpServerConfig("browser", "https://mcp.example.test/sse"),
                new CapturingFactory(session));
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        Optional<Object> found = client.getToolInfo("browser_navigate", McpServerConfig.NO_TIMEOUT);
        Optional<Object> missing = client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT);

        assertTrue(found.isPresent());
        assertTrue(missing.isEmpty());
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(session.closed);
    }

    private static final class CapturingFactory implements PlaywrightClient.TransportFactory {
        private final FakeSession session;
        private boolean opened;
        private PlaywrightClient.TransportType transportType;
        private Object serverPath;

        private CapturingFactory(FakeSession session) {
            this.session = session;
        }

        @Override
        public PlaywrightClient.TransportSession open(PlaywrightClient.TransportType transportType, Object serverPath,
                                                      McpServerConfig config, float timeout) {
            this.opened = true;
            this.transportType = transportType;
            this.serverPath = serverPath;
            return session;
        }
    }

    private static final class FakeSession implements PlaywrightClient.TransportSession {
        private final List<Object> tools = new ArrayList<>();
        private boolean initialized;
        private boolean closed;
        private String calledToolName;
        private Map<String, Object> calledArguments;
        private Object callResult;

        @Override
        public void initialize() {
            initialized = true;
        }

        @Override
        public List<?> listTools() {
            return tools;
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments) {
            calledToolName = toolName;
            calledArguments = arguments;
            return callResult;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
