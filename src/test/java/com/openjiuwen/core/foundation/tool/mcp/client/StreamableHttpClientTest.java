/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.mcp.McpBase;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpTool;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;

import io.modelcontextprotocol.spec.McpSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code StreamableHttpClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
 *
 * <p>Mirrors Python's streamable HTTP MCP client tests in
 * {@code tests/unit_tests/core/foundation/tool/test_streamable_http_client.py}.</p>
 */
class StreamableHttpClientTest {

    @Test
    void stringConfigNormalizesNameAuthAndNoTimeout() throws Exception {
        FakeSession session = new FakeSession();
        CapturingFactory factory = new CapturingFactory(session);
        Map<String, String> headers = Map.of("Authorization", "Bearer token");
        Map<String, String> query = Map.of("api_key", "secret");

        StreamableHttpClient client = new StreamableHttpClient(
                "https://mcp.example.test/mcp?existing=1",
                "   ",
                headers,
                query,
                factory);

        assertEquals("streamable-http", client.getName());
        assertEquals("https://mcp.example.test/mcp?existing=1", client.getServerPath());
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        assertEquals(60.0F, factory.timeout);
        assertEquals("streamable-http", factory.config.getServerId());
        assertEquals("streamable-http", factory.config.getServerName());
        assertEquals("streamable-http", factory.config.getClientType());
        assertNotNull(factory.authProvider);
        assertEquals(headers, factory.authProvider.getHeaders());
        assertEquals(query, factory.authProvider.getQueryParams());
        assertTrue(session.initialized);
        assertFalse(client.isDisconnected());
    }

    @Test
    void mcpConfigObjectIsUsedWithoutChangingClientType() {
        McpServerConfig config = new McpServerConfig(
                "srv-1",
                "configured-name",
                "https://mcp.example.test/messages",
                "custom-type",
                Map.of("keep", true),
                Map.of(),
                Map.of());
        CapturingFactory factory = new CapturingFactory(new FakeSession());
        StreamableHttpClient client = new StreamableHttpClient(config, factory);

        assertTrue(client.connect(1, 2.5F));

        assertSame(config, factory.config);
        assertEquals("custom-type", config.getClientType());
        assertEquals("configured-name", client.getName());
        assertEquals(2.5F, factory.timeout);
    }

    @Test
    void connectFailureReturnsFalseAndClosesEnteredSession() {
        FakeSession session = new FakeSession();
        session.initializeError = new IllegalStateException("initialize failed");
        StreamableHttpClient client = new StreamableHttpClient(
                new McpServerConfig("stream", "https://mcp.example.test/mcp"),
                new CapturingFactory(session));

        assertFalse(client.connect(1, McpServerConfig.NO_TIMEOUT));
        assertTrue(session.closed);
        assertTrue(client.isDisconnected());
    }

    @Test
    void listAndCallRequireConnectionAndExtractLatestTextContent() throws Exception {
        StreamableHttpClient unconnected = new StreamableHttpClient(
                new McpServerConfig("stream", "https://mcp.example.test/mcp"),
                new CapturingFactory(new FakeSession()));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> unconnected.listTools(McpServerConfig.NO_TIMEOUT));
        assertEquals("Not connected to streamable-http server", error.getMessage());

        FakeSession session = new FakeSession();
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("city", Map.of("type", "string")),
                List.of("city"),
                null,
                null,
                null);
        session.tools.add(new McpSchema.Tool("weather", null, "Weather", inputSchema, Map.of(), null, null));
        session.callResult = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("old"), new McpSchema.TextContent("latest")),
                false,
                null,
                null);
        StreamableHttpClient connected = new StreamableHttpClient(
                new McpServerConfig("stream", "https://mcp.example.test/mcp"),
                new CapturingFactory(session));
        assertTrue(connected.connect(1, McpServerConfig.NO_TIMEOUT));

        List<Object> tools = connected.listTools(McpServerConfig.NO_TIMEOUT);
        McpToolCard card = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("weather", card.getName());
        assertEquals("stream", card.getServerName());
        assertEquals("Weather", card.getDescription());
        assertEquals("object", card.getInputParams().get("type"));

        assertEquals("latest", connected.callTool("weather", Map.of("city", "Shenzhen"),
                McpServerConfig.NO_TIMEOUT));
        assertEquals("weather", session.calledToolName);
        assertEquals(Map.of("city", "Shenzhen"), session.calledArguments);
    }

    @Test
    void resourcesGetToolInfoAndDisconnectMirrorPythonSessionOperations() throws Exception {
        FakeSession session = new FakeSession();
        session.tools.add(new McpToolCard(null, "lookup", "Lookup", Map.of("type", "object"),
                "ignored", "ignored-server"));
        session.resources.add("resource-a");
        session.readResourceResult = List.of("content-a");
        StreamableHttpClient client = new StreamableHttpClient(
                new McpServerConfig("srv-1", "stream", "https://mcp.example.test/mcp", "streamable-http",
                        Map.of(), Map.of(), Map.of()),
                new CapturingFactory(session));
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        Optional<Object> found = client.getToolInfo("lookup", McpServerConfig.NO_TIMEOUT);
        Optional<Object> missing = client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT);

        assertTrue(found.isPresent());
        assertTrue(missing.isEmpty());
        assertEquals(List.of("resource-a"), client.listResources(McpServerConfig.NO_TIMEOUT));
        assertEquals(List.of("content-a"), client.readResource("file://resource-a", McpServerConfig.NO_TIMEOUT));
        assertEquals("file://resource-a", session.readUri);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT));
        assertTrue(session.closed);
        assertTrue(client.isDisconnected());
    }

    @Test
    void pythonParityMcpToolDropsMissingOptionalArguments() throws Exception {
        FakeSession session = new FakeSession();
        session.callResult = Map.of("content", List.of(Map.of("text", "typed")));
        StreamableHttpClient client = new StreamableHttpClient(
                new McpServerConfig("streamable-server", "http://127.0.0.1:8930/mcp"),
                new CapturingFactory(session));
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        McpToolCard card = new McpToolCard(
                "tool-id",
                "browser_type",
                "Type text",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "ref", Map.of("type", "string"),
                                "text", Map.of("type", "string"),
                                "submit", Map.of("type", "boolean"),
                                "slowly", Map.of("type", "boolean")),
                        "required", List.of("ref", "text")),
                "streamable-server",
                "streamable-server");
        McpTool tool = new McpTool(client, card);

        Object result = tool.invoke(Map.of("ref", "q", "text", "wireless mouse"));

        assertEquals(Map.of("result", "typed"), result);
        assertEquals("browser_type", session.calledToolName);
        assertEquals(Map.of("ref", "q", "text", "wireless mouse"), session.calledArguments);
    }

    @Test
    void pythonParityMcpToolPreservesEmptyObjectArguments() throws Exception {
        FakeSession session = new FakeSession();
        session.callResult = Map.of("content", List.of(Map.of("text", "snapshotted")));
        StreamableHttpClient client = new StreamableHttpClient(
                new McpServerConfig("streamable-server", "http://127.0.0.1:8930/mcp"),
                new CapturingFactory(session));
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));
        McpToolCard card = new McpToolCard(
                "tool-id",
                "browser_snapshot",
                "Capture accessibility snapshot",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "filename", Map.of("type", "string"),
                                "depth", Map.of("type", "number")),
                        "additionalProperties", false),
                "streamable-server",
                "streamable-server");
        McpTool tool = new McpTool(client, card);

        Object result = tool.invoke(Map.of());

        assertEquals(Map.of("result", "snapshotted"), result);
        assertEquals("browser_snapshot", session.calledToolName);
        assertEquals(Map.of(), session.calledArguments);
    }

    @Test
    void pythonParityImageContentReturnsCompactDescription() {
        Object result = McpBase.extractMcpToolResultContent(Map.of(
                "content", List.of(Map.of("mimeType", "image/png", "data", "abc123"))));

        assertEquals("[image content: image/png, 6 base64 chars]", result);
    }

    /**
     * Mirrors Python's injected streamable HTTP client context in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    private static final class CapturingFactory implements StreamableHttpClient.TransportFactory {
        private final FakeSession session;
        private String serverPath;
        private McpServerConfig config;
        private float timeout;
        private AuthHeaderAndQueryProvider authProvider;

        private CapturingFactory(FakeSession session) {
            this.session = session;
        }

        @Override
        public StreamableHttpClient.TransportSession open(String serverPath, McpServerConfig config, float timeout,
                                                          AuthHeaderAndQueryProvider authProvider) {
            this.serverPath = serverPath;
            this.config = config;
            this.timeout = timeout;
            this.authProvider = authProvider;
            return session;
        }
    }

    /**
     * Mirrors Python's initialized {@code ClientSession} collaborator in
     * {@code openjiuwen/core/foundation/tool/mcp/client/streamable_http_client.py}.
     */
    private static final class FakeSession implements StreamableHttpClient.TransportSession {
        private final List<Object> tools = new ArrayList<>();
        private final List<Object> resources = new ArrayList<>();
        private RuntimeException initializeError;
        private boolean initialized;
        private boolean closed;
        private String calledToolName;
        private Map<String, Object> calledArguments;
        private Object callResult;
        private Object readResourceResult;
        private String readUri;

        @Override
        public void initialize() {
            if (initializeError != null) {
                throw initializeError;
            }
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
        public List<?> listResources() {
            return resources;
        }

        @Override
        public Object readResource(String uri) {
            readUri = uri;
            return readResourceResult;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

}
