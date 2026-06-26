/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.auth.AuthHeaderAndQueryProvider;
import com.openjiuwen.core.foundation.tool.auth.AuthType;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthConfig;
import com.openjiuwen.core.foundation.tool.auth.ToolAuthResult;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.runner.callback.ToolCallEvents;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.TestAbortedException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Supplemental MCP auth parity tests.
 *
 * <p>Mirrors Python's {@code tests.unit_tests.core.foundation.tool.test_auth} in
 * {@code tests/unit_tests/core/foundation/tool/test_auth.py}.</p>
 */
class McpClientAuthPythonParityTest {

    private static final String SOURCE = "tests/unit_tests/core/foundation/tool/test_auth.py";

    @TestFactory
    Collection<DynamicTest> pythonMcpAuthCases() {
        return List.of(
                caseOf("TestSseClientAuth::test_sse_client_auth_flow",
                        McpClientAuthPythonParityTest::sseClientAuthFlow),
                caseOf("TestSseClientAuth::test_ssl_auth_missing_cert_raises_exception",
                        McpClientAuthPythonParityTest::sslAuthMissingCertRaisesException),
                caseOf("TestStreamableHttpClientAuth::test_streamable_http_client_auth_flow",
                        McpClientAuthPythonParityTest::streamableHttpClientAuthFlow)
        );
    }

    private static DynamicTest caseOf(String pythonNode, Executable executable) {
        return dynamicTest(SOURCE + "::" + pythonNode, executable);
    }

    private static void sseClientAuthFlow() {
        AuthHeaderAndQueryProvider provider = new AuthHeaderAndQueryProvider(
                Map.of("Authorization", "Bearer test-token"),
                Map.of("api_key", "test-key")
        );
        CapturingSseFactory factory = new CapturingSseFactory();
        CapturingAuthTrigger trigger = new CapturingAuthTrigger(Arrays.asList(
                null,
                new ToolAuthResult(true, Map.of("auth_provider", provider), "", null)
        ));
        SseClient client = new SseClient(mcpConfig("sse"), factory, trigger);

        assertTrue(client.connect(1, 10.0D).join());

        assertEquals(ToolCallEvents.TOOL_AUTH, trigger.event);
        assertEquals(AuthType.HEADER_AND_QUERY.getValue(), trigger.authConfig.getAuthType());
        assertEquals(Map.of("Authorization", "Bearer test-token"), trigger.authConfig.getConfig().get("auth_headers"));
        assertEquals(Map.of("api_key", "test-key"), trigger.authConfig.getConfig().get("auth_query_params"));
        assertEquals("test-server", trigger.authConfig.getToolType());
        assertEquals("test-server-id", trigger.authConfig.getToolId());
        assertEquals(provider, factory.authProvider);
        assertTrue(factory.session.initialized);
    }

    private static void sslAuthMissingCertRaisesException() {
        throw new TestAbortedException("Skipped in Python source: cannot operate normally in pipeline");
    }

    private static void streamableHttpClientAuthFlow() {
        CapturingStreamableFactory factory = new CapturingStreamableFactory();
        StreamableHttpClient client = new StreamableHttpClient(mcpConfig("streamable-http"), factory);

        assertTrue(client.connect(1, McpServerConfig.NO_TIMEOUT));

        AuthHeaderAndQueryProvider provider = assertInstanceOf(
                AuthHeaderAndQueryProvider.class,
                factory.authProvider
        );
        assertEquals(Map.of("Authorization", "Bearer test-token"), provider.getHeaders());
        assertEquals(Map.of("api_key", "test-key"), provider.getQueryParams());
        assertEquals("test-server", factory.config.getServerName());
        assertEquals("test-server-id", factory.config.getServerId());
        assertTrue(factory.session.initialized);
    }

    private static McpServerConfig mcpConfig(String clientType) {
        return new McpServerConfig(
                "test-server-id",
                "test-server",
                "http://127.0.0.1:8930/mcp",
                clientType,
                Map.of(),
                Map.of("Authorization", "Bearer test-token"),
                Map.of("api_key", "test-key")
        );
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
        private Object authProvider;

        @Override
        public SseClient.SseTransportSession open(String serverPath, double timeout, Object authProvider) {
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

    private static final class CapturingStreamableFactory implements StreamableHttpClient.TransportFactory {
        private final FakeStreamableSession session = new FakeStreamableSession();
        private McpServerConfig config;
        private AuthHeaderAndQueryProvider authProvider;

        @Override
        public StreamableHttpClient.TransportSession open(String serverPath, McpServerConfig config, float timeout,
                                                          AuthHeaderAndQueryProvider authProvider) {
            this.config = config;
            this.authProvider = authProvider;
            return session;
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
