/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_validation} in
 * {@code tests/unit_tests/core/foundation/tool/test_validation.py}.
 */
class McpClientValidationPythonParityTest {

    @Test
    void testSseClientAuthValidation() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(authHeaders(), authQueryParams());
        CapturingSseFactory factory = new CapturingSseFactory();
        CapturingAuthTrigger trigger = new CapturingAuthTrigger(Arrays.asList(
                null,
                new ToolAuthResult(true, Map.of("auth_provider", provider), "", null)
        ));
        SseClient client = new SseClient(serverConfig(
                "test-sse-server-id",
                "test-sse-server",
                "http://127.0.0.1:8080/sse",
                "sse"
        ), factory, trigger);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT).join());

        assertEquals(ToolCallEvents.TOOL_AUTH, trigger.event);
        assertEquals("test-sse-server", trigger.authConfig.getToolType());
        assertEquals("test-sse-server-id", trigger.authConfig.getToolId());
        assertEquals(authHeaders(), trigger.authConfig.getConfig().get("auth_headers"));
        assertEquals(authQueryParams(), trigger.authConfig.getConfig().get("auth_query_params"));
        assertEquals("http://127.0.0.1:8080/sse", factory.serverPath);
        assertEquals(60.0d, factory.timeout);
        assertSame(provider, factory.authProvider);
        assertTrue(factory.session.initialized);
    }

    @Test
    void testStreamableHttpClientAuthValidation() {
        CapturingStreamableFactory factory = new CapturingStreamableFactory();
        StreamableHttpClient client = new StreamableHttpClient(serverConfig(
                "test-streamable-server-id",
                "test-streamable-server",
                "http://127.0.0.1:8080/streamable",
                "streamable-http"
        ), factory);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        assertEquals("http://127.0.0.1:8080/streamable", factory.serverPath);
        assertEquals(60.0f, factory.timeout);
        assertEquals("test-streamable-server", factory.config.getServerName());
        assertEquals("test-streamable-server-id", factory.config.getServerId());
        assertEquals(authHeaders(), factory.authProvider.getHeaders());
        assertEquals(authQueryParams(), factory.authProvider.getQueryParams());
        assertTrue(factory.session.initialized);
    }

    private static McpServerConfig serverConfig(String id, String name, String path, String clientType) {
        return new McpServerConfig(id, name, path, clientType, Map.of(), authHeaders(), authQueryParams());
    }

    private static Map<String, String> authHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer test_token");
        headers.put("X-Custom-Header", "test_value");
        return headers;
    }

    private static Map<String, String> authQueryParams() {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("api_key", "test_key");
        queryParams.put("version", "v1");
        return queryParams;
    }

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

    private static final class CapturingSseFactory implements SseClient.SseTransportFactory {
        private final FakeSseSession session = new FakeSseSession();
        private String serverPath;
        private double timeout;
        private Object authProvider;

        @Override
        public SseClient.SseTransportSession open(String serverPath, double timeout, Object authProvider) {
            this.serverPath = serverPath;
            this.timeout = timeout;
            this.authProvider = authProvider;
            return session;
        }
    }

    private static final class CapturingStreamableFactory implements StreamableHttpClient.TransportFactory {
        private final FakeStreamableSession session = new FakeStreamableSession();
        private String serverPath;
        private McpServerConfig config;
        private float timeout;
        private AuthHeaderAndQueryProvider authProvider;

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

    private static final class FakeSseSession implements SseClient.SseTransportSession {
        private boolean initialized;

        @Override
        public void initialize() {
            initialized = true;
        }

        @Override
        public List<?> listTools() {
            return List.of();
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments) {
            return Map.of();
        }

        @Override
        public List<?> listResources() {
            return List.of();
        }

        @Override
        public Object readResource(String uri) {
            return List.of();
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeStreamableSession implements StreamableHttpClient.TransportSession {
        private boolean initialized;

        @Override
        public void initialize() {
            initialized = true;
        }

        @Override
        public List<?> listTools() {
            return List.of();
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments) {
            return Map.of();
        }

        @Override
        public List<?> listResources() {
            return List.of();
        }

        @Override
        public Object readResource(String uri) {
            return List.of();
        }

        @Override
        public void close() {
        }
    }
}
