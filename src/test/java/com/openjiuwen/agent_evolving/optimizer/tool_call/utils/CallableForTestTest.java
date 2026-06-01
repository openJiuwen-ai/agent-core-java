/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the MCP callable test utility.
 *
 * <p>Mirrors Python's {@code callable_fortest} in
 * {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.callable_fortest}.
 */
class CallableForTestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void schemaContainsSearchFundsFunction() throws Exception {
        JsonNode root = MAPPER.readTree(CallableForTest.getDescription());

        assertTrue(root.has("type"));
        assertEquals("function", root.get("type").asText());

        JsonNode func = root.get("function");
        assertTrue(func.has("name"));
        assertEquals("SearchFunds", func.get("name").asText());
    }

    @Test
    void schemaFunctionHasParameters() throws Exception {
        JsonNode root = MAPPER.readTree(CallableForTest.getDescription());
        JsonNode params = root.get("function").get("parameters");

        assertTrue(params.has("type"));
        assertEquals("object", params.get("type").asText());
        assertTrue(params.has("properties"));

        JsonNode props = params.get("properties");
        assertTrue(props.has("category"));
        assertTrue(props.has("keyword"));
        assertTrue(props.has("size"));
        assertTrue(props.has("page"));
    }

    @Test
    void schemaCategoryPropertyHasDescription() throws Exception {
        JsonNode root = MAPPER.readTree(CallableForTest.getDescription());
        JsonNode category = root.get("function").get("parameters").get("properties").get("category");

        assertEquals("string", category.get("type").asText());
        assertTrue(category.has("description"));
        assertFalse(category.get("description").asText().isEmpty());
    }

    @Test
    void toolMapContainsNameAndDescription() {
        Map<String, Object> tool = CallableForTest.getTool();

        assertEquals("SearchFunds", tool.get("name"));
        assertNotNull(tool.get("description"));
        assertTrue(((String) tool.get("description")).contains("SearchFunds"));
    }

    @Test
    void mcpUrlDefaultsToEmptyString() {
        assertNotNull(CallableForTest.MCP_URL);
    }

    @Test
    void mcpNameDefaultsToExpectedValue() {
        assertEquals(
                System.getenv().getOrDefault("MCP_NAME", "Streamable HTTP Python Server"),
                CallableForTest.MCP_NAME);
    }

    @Test
    void makeSyncMcpCallerForwardsToolNameAndArguments() throws Exception {
        FakeMcpClient client = new FakeMcpClient("https://example.com/sse");
        CallableForTest.McpToolCaller caller = CallableForTest.makeSyncMcpCaller(
                "https://example.com/sse",
                "S",
                config -> client
        );

        Object result = caller.call(Map.of("name", "SearchFunds", "arguments", Map.of("keyword", "abc")));

        assertEquals("ok-result", result);
        assertEquals("SearchFunds", client.lastToolName);
        assertEquals(Map.of("keyword", "abc"), client.lastArguments);
        assertEquals("https://example.com/sse", client.getServerPath());
        assertTrue(client.connected.get());
        assertTrue(client.disconnected.get());
    }

    @Test
    void makeSyncMcpCallerParsesJsonArguments() throws Exception {
        FakeMcpClient client = new FakeMcpClient("https://example.com/sse");
        CallableForTest.McpToolCaller caller = CallableForTest.makeSyncMcpCaller(
                "https://example.com/sse",
                "S",
                config -> client
        );

        Object result = caller.call(Map.of("name", "SearchFunds", "arguments", "{\"keyword\":\"abc\"}"));

        assertEquals("ok-result", result);
        assertEquals(Map.of("keyword", "abc"), client.lastArguments);
    }

    @Test
    void makeSyncMcpCallerRejectsInvalidJsonArguments() {
        FakeMcpClient client = new FakeMcpClient("https://example.com/sse");
        CallableForTest.McpToolCaller caller = CallableForTest.makeSyncMcpCaller(
                "https://example.com/sse",
                "S",
                config -> client
        );

        assertThrows(IllegalArgumentException.class,
                () -> caller.call(Map.of("name", "SearchFunds", "arguments", "{bad-json")));
    }

    private static final class FakeMcpClient implements McpClient {
        private final AtomicBoolean connected = new AtomicBoolean(false);
        private final AtomicBoolean disconnected = new AtomicBoolean(false);
        private final String serverPath;
        private String lastToolName;
        private Map<String, Object> lastArguments = Map.of();

        private FakeMcpClient(String serverPath) {
            this.serverPath = serverPath;
        }

        @Override
        public boolean connect(int retryTimes, float timeout) {
            connected.set(true);
            return true;
        }

        @Override
        public boolean disconnect(float timeout) {
            disconnected.set(true);
            return true;
        }

        @Override
        public List<Object> listTools(float timeout) {
            return List.of();
        }

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments, float timeout) {
            lastToolName = toolName;
            lastArguments = arguments != null ? Map.copyOf(arguments) : Map.of();
            return Map.of("content", List.of(Map.of("text", "ok-result")));
        }

        @Override
        public Optional<Object> getToolInfo(String toolName, float timeout) {
            return Optional.empty();
        }

        @Override
        public String getServerPath() {
            return serverPath;
        }
    }
}
