/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.auth.AuthType;
import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code SseClient} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
 */
class SseClientTest {

    @Test
    void connectTriggersAuthAndUsesDefaultTimeout() {
        McpServerConfig config = config();
        FakeSession session = new FakeSession();
        CapturingFactory factory = new CapturingFactory(session);
        CapturingAuthTrigger authTrigger = new CapturingAuthTrigger(List.of(
                new ToolAuthResult(true, Map.<String, Object>of("auth_provider", "first"), "", null),
                new ToolAuthResult(false, Map.<String, Object>of("auth_provider", "bad"), "", null),
                new ToolAuthResult(true, Map.<String, Object>of("auth_provider", "last"), "", null)
        ));

        SseClient client = new SseClient(config, factory, authTrigger);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT).join());
        assertEquals(SseClient.CLIENT_NAME, SseClient.getClientName());
        assertEquals("sse", client.getMetadata().get("client_name"));
        assertEquals("mcp", client.getMetadata().get("client_type"));
        assertEquals(ToolCallEvents.TOOL_AUTH, authTrigger.event);
        assertEquals(AuthType.HEADER_AND_QUERY.getValue(), authTrigger.authConfig.getAuthType());
        assertEquals("sse-server", authTrigger.authConfig.getToolType());
        assertEquals("srv-sse", authTrigger.authConfig.getToolId());
        assertEquals(Map.of("Authorization", "Bearer token"), authTrigger.authConfig.getConfig().get("auth_headers"));
        assertEquals(Map.of("tenant", "demo"), authTrigger.authConfig.getConfig().get("auth_query_params"));
        assertEquals("https://mcp.example.test/sse", factory.serverPath);
        assertEquals(60.0D, factory.timeout);
        assertEquals("last", factory.authProvider);
        assertEquals("last", client.getAuthProvider());
        assertTrue(session.initialized);
        assertFalse(client.isDisconnected());
    }

    @Test
    void defaultAuthTriggerCreatesHeaderAndQueryProvider() {
        FakeSession session = new FakeSession();
        CapturingFactory factory = new CapturingFactory(session);
        SseClient client = new SseClient(config(), factory, SseClient.AuthTrigger.defaultAuth());

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT).join());

        AuthHeaderAndQueryProvider provider = assertInstanceOf(AuthHeaderAndQueryProvider.class,
                factory.authProvider);
        assertEquals(Map.of("Authorization", "Bearer token"), provider.getHeaders());
        assertEquals(Map.of("tenant", "demo"), provider.getQueryParams());
    }

    @Test
    void extractAuthProviderMatchesPythonReverseOrdering() {
        assertEquals(null, SseClient.extractAuthProvider(null));
        assertEquals(null, SseClient.extractAuthProvider(new ToolAuthResult(
                false, Map.<String, Object>of("auth_provider", "bad"), "", null)));

        Object value = SseClient.extractAuthProvider(Arrays.asList(
                new AuthDataCarrier(Map.<String, Object>of("auth_provider", "first")),
                null,
                new ToolAuthResult(false, Map.<String, Object>of("auth_provider", "bad"), "", null),
                new ToolAuthResult(true, Map.<String, Object>of("auth_provider", "last-good"), "", null)
        ));

        assertEquals("last-good", value);
        assertEquals("array-good", SseClient.extractAuthProvider(new Object[]{
                new AuthDataCarrier(Map.<String, Object>of("auth_provider", "array-first")),
                new AuthDataCarrier(Map.<String, Object>of("auth_provider", "array-good"))
        }));
    }

    @Test
    void connectReturnsFalseWhenFactoryCannotOpen() {
        CapturingFactory factory = new CapturingFactory(new FakeSession());
        factory.openError = new IllegalStateException("boom");
        SseClient client = new SseClient(config(), factory, (event, authConfig) -> null);

        assertFalse(client.connect(1, McpServerConfig.NO_TIMEOUT).join());
        assertTrue(factory.opened);
    }

    @Test
    void operationsRequireConnectedSession() {
        SseClient client = new SseClient(config(), new CapturingFactory(new FakeSession()),
                (event, authConfig) -> null);

        assertNotConnected(() -> client.listTools(McpServerConfig.NO_TIMEOUT).join());
        assertNotConnected(() -> client.callTool("lookup", Map.of(), McpServerConfig.NO_TIMEOUT).join());
        assertNotConnected(() -> client.getToolInfo("lookup", McpServerConfig.NO_TIMEOUT).join());
        assertNotConnected(() -> client.listResources(McpServerConfig.NO_TIMEOUT).join());
        assertNotConnected(() -> client.readResource("file://a", McpServerConfig.NO_TIMEOUT).join());
    }

    @Test
    void listToolsMapsSessionToolsAndGetToolInfoFindsMatch() {
        FakeSession session = new FakeSession();
        session.tools.add(new ToolLike("lookup", "Lookup", Map.of("type", "object")));
        session.tools.add(Map.of("name", "search", "description", "Search",
                "inputSchema", Map.of("properties", Map.of("q", Map.of("type", "string")))));
        SseClient client = connectedClient(session);

        List<Object> tools = client.listTools(McpServerConfig.NO_TIMEOUT).join();

        McpToolCard first = assertInstanceOf(McpToolCard.class, tools.get(0));
        assertEquals("lookup", first.getName());
        assertEquals("Lookup", first.getDescription());
        assertEquals("sse-server", first.getServerName());
        assertEquals("srv-sse", first.getServerId());
        assertEquals(Map.of("type", "object"), first.getInputParams());
        McpToolCard second = assertInstanceOf(McpToolCard.class, tools.get(1));
        assertEquals("search", second.getName());
        assertTrue(second.getInputParams().containsKey("properties"));

        Optional<Object> found = client.getToolInfo("search", McpServerConfig.NO_TIMEOUT).join();
        Optional<Object> missing = client.getToolInfo("missing", McpServerConfig.NO_TIMEOUT).join();
        assertTrue(found.isPresent());
        assertTrue(missing.isEmpty());
    }

    @Test
    void callToolExtractsLatestResultContentAndPassesArguments() {
        FakeSession session = new FakeSession();
        session.callResult = Map.of("content", List.of(Map.of("text", "old"), Map.of("text", "latest")));
        SseClient client = connectedClient(session);

        Object result = client.callTool("lookup", Map.of("query", "hello"), McpServerConfig.NO_TIMEOUT).join();

        assertEquals("latest", result);
        assertEquals("lookup", session.lastToolName);
        assertEquals(Map.of("query", "hello"), session.lastArguments);
    }

    @Test
    void resourcesAreDelegatedAndDisconnectIsIdempotent() {
        FakeSession session = new FakeSession();
        session.resources = List.of(Map.of("uri", "file://a"));
        session.contents = List.of(Map.of("text", "body"));
        SseClient client = connectedClient(session);

        assertEquals(session.resources, client.listResources(McpServerConfig.NO_TIMEOUT).join());
        assertSame(session.contents, client.readResource("file://a", McpServerConfig.NO_TIMEOUT).join());
        assertEquals("file://a", session.lastResourceUri);
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT).join());
        assertTrue(client.disconnect(McpServerConfig.NO_TIMEOUT).join());
        assertTrue(session.closed);
        assertEquals(1, session.closeCalls);
    }

    private static SseClient connectedClient(FakeSession session) {
        SseClient client = new SseClient(config(), new CapturingFactory(session), (event, authConfig) -> null);
        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT).join());
        return client;
    }

    private static McpServerConfig config() {
        return new McpServerConfig("srv-sse", "sse-server", "https://mcp.example.test/sse", "sse",
                Map.of(), Map.of("Authorization", "Bearer token"), Map.of("tenant", "demo"));
    }

    private static void assertNotConnected(Runnable operation) {
        CompletionException error = assertThrows(CompletionException.class, operation::run);
        assertInstanceOf(RuntimeException.class, error.getCause());
        assertEquals("Not connected to SSE server", error.getCause().getMessage());
    }

    /**
     * Mirrors Python's callback return used by {@code SseClient._extract_auth_provider} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
     */
    private static final class AuthDataCarrier {
        private final Map<String, Object> auth_data;

        private AuthDataCarrier(Map<String, Object> authData) {
            this.auth_data = authData;
        }
    }

    /**
     * Mirrors Python's auth framework trigger boundary used by {@code SseClient.connect()} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
     */
    private static final class CapturingAuthTrigger implements SseClient.AuthTrigger {
        private final Object result;
        private String event;
        private ToolAuthConfig authConfig;

        private CapturingAuthTrigger(Object result) {
            this.result = result;
        }

        @Override
        public Object trigger(String event, ToolAuthConfig authConfig) {
            this.event = event;
            this.authConfig = authConfig;
            return result;
        }
    }

    /**
     * Mirrors Python's {@code sse_client(...)} factory boundary in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
     */
    private static final class CapturingFactory implements SseClient.SseTransportFactory {
        private final FakeSession session;
        private boolean opened;
        private String serverPath;
        private double timeout;
        private Object authProvider;
        private RuntimeException openError;

        private CapturingFactory(FakeSession session) {
            this.session = session;
        }

        @Override
        public SseClient.SseTransportSession open(String serverPath, double timeout, Object authProvider) {
            this.opened = true;
            this.serverPath = serverPath;
            this.timeout = timeout;
            this.authProvider = authProvider;
            if (openError != null) {
                throw openError;
            }
            return session;
        }
    }

    /**
     * Mirrors Python's initialized {@code ClientSession} behavior used by {@code SseClient} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
     */
    private static final class FakeSession implements SseClient.SseTransportSession {
        private final List<Object> tools = new ArrayList<>();
        private boolean initialized;
        private boolean closed;
        private int closeCalls;
        private Object callResult = Map.of("content", List.of());
        private String lastToolName;
        private Map<String, Object> lastArguments;
        private List<Object> resources = List.of();
        private Object contents;
        private String lastResourceUri;

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
            lastToolName = toolName;
            lastArguments = arguments;
            return callResult;
        }

        @Override
        public List<?> listResources() {
            return resources;
        }

        @Override
        public Object readResource(String uri) {
            lastResourceUri = uri;
            return contents;
        }

        @Override
        public void close() {
            closed = true;
            closeCalls++;
        }
    }

    /**
     * Mirrors Python MCP tool objects consumed by {@code SseClient.list_tools()} in
     * {@code openjiuwen/core/foundation/tool/mcp/client/sse_client.py}.
     */
    private static final class ToolLike {
        private final String name;
        private final String description;
        private final Map<String, Object> inputSchema;

        private ToolLike(String name, String description, Map<String, Object> inputSchema) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
        }

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public Map<String, Object> inputSchema() {
            return inputSchema;
        }
    }
}
