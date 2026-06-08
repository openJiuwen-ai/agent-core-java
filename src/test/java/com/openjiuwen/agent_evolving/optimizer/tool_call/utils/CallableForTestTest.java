/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallableForTestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void schemaContainsSearchFundsFunction() throws Exception {
        JsonNode root = MAPPER.readTree(CallableForTest.getDescription());
        assertEquals("function", root.get("type").asText());
        assertEquals("SearchFunds", root.get("function").get("name").asText());
    }

    @Test
    void makeSyncMcpCallerNormalizesArguments() throws Exception {
        FakeSession session = new FakeSession();
        CallableForTest.McpToolCaller caller = CallableForTest.makeSyncMcpCaller(
                "https://example.com/sse",
                "S",
                (url, name) -> session
        );

        Object out = caller.call(Map.of("name", "SearchFunds", "arguments", Map.of("keyword", "abc")));
        assertEquals("ok-result", out);
        assertEquals("SearchFunds", session.toolName);
        assertEquals(Map.of("keyword", "abc"), session.arguments);

        Object out2 = caller.call(Map.of("name", "SearchFunds", "arguments", "{\"keyword\":\"abc\"}"));
        assertEquals("ok-result", out2);
        assertEquals(Map.of("keyword", "abc"), session.arguments);
    }

    @Test
    void makeSyncMcpCallerRejectsInvalidJsonArguments() {
        FakeSession session = new FakeSession();
        CallableForTest.McpToolCaller caller = CallableForTest.makeSyncMcpCaller(
                "https://example.com/sse",
                "S",
                (url, name) -> session
        );

        assertThrows(IllegalArgumentException.class,
                () -> caller.call(Map.of("name", "SearchFunds", "arguments", "{bad-json")));
    }

    @Test
    void toolMapContainsNameAndDescription() {
        assertEquals("SearchFunds", CallableForTest.getTool().get("name"));
        assertTrue(String.valueOf(CallableForTest.getTool().get("description")).contains("SearchFunds"));
    }

    private static final class FakeSession implements CallableForTest.McpSession {
        private String toolName;
        private Map<String, Object> arguments = Map.of();

        @Override
        public Object callTool(String toolName, Map<String, Object> arguments) {
            this.toolName = toolName;
            this.arguments = arguments;
            return Map.of("content", java.util.List.of(Map.of("text", "ok-result")));
        }
    }
}
