/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.foundation.tool.mcp.McpToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.resourcemanager.ToolMgr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for StreamableHttpClient.
 * <p>
 * Mirrors Python's {@code test_streamable_http_client.py} from
 * {@code tests/unit_tests/core/foundation/tool/test_streamable_http_client.py}.
 * </p>
 */
@DisplayName("StreamableHttpClient Tests")
class TestStreamableHttpClientDetailed {

    @AfterEach
    void resetClientFactory() {
        ToolMgr.setClientFactoryOverrideForTesting(null);
    }

    @Test
    void testConnectListCallDisconnectLifecycle() throws Exception {
        FakeMcpClient delegate = new FakeMcpClient("http://127.0.0.1:8930/mcp",
                List.of(card("browser_navigate", "Navigate page", schema(Map.of(), List.of())),
                        card("browser_extract_text", "Extract page text", schema(Map.of(), List.of()))),
                (toolName, arguments) -> textResult("ok:" + toolName + ":" + arguments.getOrDefault("url", "")));
        StreamableHttpClient client = new StreamableHttpClient(config("test-server"), delegate);

        assertTrue(client.connect());
        assertEquals("http://127.0.0.1:8930/mcp", client.getServerPath());
        assertEquals(60.0f, delegate.lastConnectTimeout);

        List<Object> tools = client.listTools();
        assertEquals(2, tools.size());
        assertEquals("browser_navigate", ((McpToolCard) tools.get(0)).getName());
        assertEquals("Extract page text", ((McpToolCard) tools.get(1)).getDescription());

        Object result = client.callTool("browser_navigate", Map.of("url", "https://example.com"));
        assertEquals("ok:browser_navigate:https://example.com", result);

        Optional<Object> toolInfo = client.getToolInfo("browser_extract_text");
        assertTrue(toolInfo.isPresent());
        assertEquals("browser_extract_text", ((McpToolCard) toolInfo.get()).getName());
        assertTrue(client.getToolInfo("missing").isEmpty());

        assertTrue(client.disconnect());
        assertTrue(delegate.disconnected);
    }

    @Test
    void testConnectReturnsFalseOnError() {
        FakeMcpClient delegate = new FakeMcpClient("http://127.0.0.1:8930/mcp", List.of(),
                (toolName, arguments) -> null);
        delegate.failConnect = true;
        StreamableHttpClient client = new StreamableHttpClient(config("test-server"), delegate);

        assertFalse(client.connect(1, 10.0f));
        assertTrue(delegate.disconnected);
    }

    @Test
    void testAuthProviderAddsHeadersAndQuery() throws Exception {
        StreamableHttpClient client = new StreamableHttpClient(
                "https://example.com/sse?existing=1",
                "test-server",
                Map.of("Authorization", "Bearer x"),
                Map.of("ak", "demo-ak"));

        Field configField = StreamableHttpClient.class.getDeclaredField("config");
        configField.setAccessible(true);
        McpServerConfig config = (McpServerConfig) configField.get(client);

        assertEquals("Bearer x", config.getAuthHeaders().get("Authorization"));
        assertEquals("demo-ak", config.getAuthQueryParams().get("ak"));
        assertEquals("https://example.com/sse?existing=1", config.getServerPath());
    }

    @Test
    void testMcpServerStreamableHttpLifecycle() throws Exception {
        FakeMcpClient fakeClient = new FakeMcpClient("http://127.0.0.1:8930/mcp",
                List.of(card("browser_navigate", "Navigate to URL",
                                schema(Map.of("url", Map.of("type", "string")), List.of("url"))),
                        card("browser_extract_text", "Extract text",
                                schema(Map.of("selector", Map.of("type", "string")), List.of("selector")))),
                (toolName, arguments) -> "navigation completed");
        ToolMgr.setClientFactoryOverrideForTesting(config -> fakeClient);
        ResourceMgr resourceMgr = new ResourceMgr();

        List<Result<String>> addResult = resourceMgr.addMcpServer(config("streamable-server"), null, null);
        assertEquals(1, addResult.size());
        assertTrue(addResult.get(0).isOk());

        List<ToolInfo> toolInfos = resourceMgr.getMcpToolInfos(null, null, "streamable-server",
                null, null, false, true);
        assertEquals(2, toolInfos.size());
        assertEquals(List.of("browser_navigate", "browser_extract_text"),
                toolInfos.stream().map(ToolInfo::getName).toList());

        List<Tool> tools = tools(resourceMgr.getMcpTool("browser_navigate", null, "streamable-server",
                null, null, false));
        assertEquals(1, tools.size());

        Map<String, Object> inputs = Map.of("url", "https://example.com");
        Object result = tools.get(0).invoke(inputs);
        assertEquals(Map.of("result", "navigation completed"), result);
        assertEquals("browser_navigate", fakeClient.lastToolName);
        assertEquals(inputs, fakeClient.lastArguments);

        List<Result<String>> removeResults = resourceMgr.removeMcpServer(null, "streamable-server",
                null, null, false);
        assertEquals(1, removeResults.size());
        assertTrue(removeResults.get(0).isOk());

        List<ToolInfo> remainingInfos = resourceMgr.getMcpToolInfos(null, null, "streamable-server",
                null, null, false, true);
        assertEquals(List.of(), remainingInfos);
    }

    @Test
    void testMcpToolDropsMissingOptionalArguments() throws Exception {
        FakeMcpClient fakeClient = new FakeMcpClient("http://127.0.0.1:8930/mcp",
                List.of(card("browser_type", "Type text",
                        schema(Map.of("ref", Map.of("type", "string"),
                                        "text", Map.of("type", "string"),
                                        "submit", Map.of("type", "boolean"),
                                        "slowly", Map.of("type", "boolean")),
                                List.of("ref", "text")))),
                (toolName, arguments) -> "typed");
        ToolMgr.setClientFactoryOverrideForTesting(config -> fakeClient);
        ResourceMgr resourceMgr = new ResourceMgr();
        assertTrue(resourceMgr.addMcpServer(config("streamable-server"), null, null).get(0).isOk());

        List<Tool> tools = tools(resourceMgr.getMcpTool("browser_type", null, "streamable-server",
                null, null, false));
        Object result = tools.get(0).invoke(Map.of("ref", "q", "text", "wireless mouse"));

        assertEquals(Map.of("result", "typed"), result);
        assertEquals("browser_type", fakeClient.lastToolName);
        assertEquals(Map.of("ref", "q", "text", "wireless mouse"), fakeClient.lastArguments);
    }

    @Test
    void testMcpToolPreservesEmptyObjectArguments() throws Exception {
        FakeMcpClient fakeClient = new FakeMcpClient("http://127.0.0.1:8930/mcp",
                List.of(card("browser_snapshot", "Capture accessibility snapshot",
                        schema(Map.of("filename", Map.of("type", "string"),
                                        "depth", Map.of("type", "number")),
                                List.of()))),
                (toolName, arguments) -> "snapshotted");
        ToolMgr.setClientFactoryOverrideForTesting(config -> fakeClient);
        ResourceMgr resourceMgr = new ResourceMgr();
        assertTrue(resourceMgr.addMcpServer(config("streamable-server"), null, null).get(0).isOk());

        List<Tool> tools = tools(resourceMgr.getMcpTool("browser_snapshot", null, "streamable-server",
                null, null, false));
        Object result = tools.get(0).invoke(Map.of());

        assertEquals(Map.of("result", "snapshotted"), result);
        assertEquals("browser_snapshot", fakeClient.lastToolName);
        assertEquals(Map.of(), fakeClient.lastArguments);
    }

    @Test
    void testImageContentReturnsCompactDescription() throws Exception {
        FakeMcpClient fakeClient = new FakeMcpClient("http://127.0.0.1:8930/mcp", List.of(),
                (toolName, arguments) -> Map.of("content",
                        List.of(Map.of("mimeType", "image/png", "data", "abc123"))));
        StreamableHttpClient client = new StreamableHttpClient(config("streamable-server"), fakeClient);

        Object result = client.callTool("screenshot", Map.of());

        assertEquals("[image content: image/png, 6 base64 chars]", result);
    }

    private static McpServerConfig config(String serverName) {
        return McpServerConfig.builder()
                .serverId(serverName)
                .serverName(serverName)
                .serverPath("http://127.0.0.1:8930/mcp")
                .clientType("streamable-http")
                .build();
    }

    private static McpToolCard card(String name, String description, Map<String, Object> inputParams) {
        return McpToolCard.builder()
                .name(name)
                .description(description)
                .serverName("streamable-server")
                .inputParams(inputParams)
                .build();
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private static Map<String, Object> textResult(String text) {
        return Map.of("content", List.of(Map.of("text", text)));
    }

    @SuppressWarnings("unchecked")
    private static List<Tool> tools(Object raw) {
        assertInstanceOf(List.class, raw);
        return (List<Tool>) raw;
    }

    private static final class FakeMcpClient implements McpClient {
        private final String serverPath;
        private final List<Object> tools;
        private final BiFunction<String, Map<String, Object>, Object> callHandler;
        private boolean failConnect;
        private boolean disconnected;
        private float lastConnectTimeout;
        private String lastToolName;
        private Map<String, Object> lastArguments = Map.of();

        private FakeMcpClient(String serverPath, List<McpToolCard> tools,
                              BiFunction<String, Map<String, Object>, Object> callHandler) {
            this.serverPath = serverPath;
            this.tools = new ArrayList<>(tools);
            this.callHandler = callHandler;
        }

        @Override
        public boolean connect(int retryTimes, float timeout) {
            if (failConnect) {
                throw new IllegalStateException("connection failed");
            }
            lastConnectTimeout = timeout;
            return true;
        }

        @Override
        public boolean disconnect(float timeout) {
            disconnected = true;
            return true;
        }

        @Override
        public List<Object> listTools(float timeout) {
            return new ArrayList<>(tools);
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            lastToolName = toolName;
            lastArguments = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
            return callHandler.apply(toolName, lastArguments);
        }

        @Override
        public Optional<Object> getToolInfo(String toolName, float timeout) {
            return tools.stream()
                    .filter(McpToolCard.class::isInstance)
                    .map(McpToolCard.class::cast)
                    .filter(card -> toolName.equals(card.getName()))
                    .map(card -> (Object) card)
                    .findFirst();
        }

        @Override
        public String getServerPath() {
            return serverPath;
        }
    }
}
