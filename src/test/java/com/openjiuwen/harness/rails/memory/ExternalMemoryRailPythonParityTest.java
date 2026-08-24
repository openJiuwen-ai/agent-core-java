/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.memory;

import com.openjiuwen.core.memory.external.MemoryProvider;
import com.openjiuwen.core.singleagent.rail.RunKind;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.harness.rails.test_external_memory_rail} in
 * {@code tests/unit_tests/harness/rails/test_external_memory_rail.py}.
 */
class ExternalMemoryRailPythonParityTest {

    @Test
    void onlyQuery() {
        assertEquals("test query", ExternalMemoryRail.resolveUserTextForMemory(ctx(Map.of("query", "test query"))));
    }

    @Test
    void onlyMessages() {
        CallbackContext ctx = ctx(Map.of("messages", List.of(
                Map.of("role", "assistant", "content", "response"),
                Map.of("role", "user", "content", "test message"))));

        assertEquals("test message", ExternalMemoryRail.resolveUserTextForMemory(ctx));
    }

    @Test
    void bothQueryAndMessagesPrioritizesQuery() {
        CallbackContext ctx = ctx(Map.of(
                "query", "query value",
                "messages", List.of(Map.of("role", "user", "content", "message value"))));

        assertEquals("query value", ExternalMemoryRail.resolveUserTextForMemory(ctx));
    }

    @Test
    void bothEmpty() {
        assertEquals("", ExternalMemoryRail.resolveUserTextForMemory(ctx(Map.of())));
    }

    @Test
    void messagesWithListContent() {
        CallbackContext ctx = ctx(Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "text", "text", "hello world"))))));

        assertEquals("hello world", ExternalMemoryRail.resolveUserTextForMemory(ctx));
    }

    @Test
    void messagesWithMultipleUserTakeLast() {
        CallbackContext ctx = ctx(Map.of("messages", List.of(
                Map.of("role", "user", "content", "first"),
                Map.of("role", "user", "content", "last"))));

        assertEquals("last", ExternalMemoryRail.resolveUserTextForMemory(ctx));
    }

    @Test
    void resultWithOutputKey() {
        assertEquals("assistant response",
                ExternalMemoryRail.extractAssistantOutput(ctx(Map.of("result", Map.of("output", "assistant response")))));
    }

    @Test
    void resultWithMessageContent() {
        assertEquals("assistant response",
                ExternalMemoryRail.extractAssistantOutput(ctx(Map.of(
                        "result", Map.of("message", Map.of("content", "assistant response"))))));
    }

    @Test
    void resultWithContentKey() {
        assertEquals("assistant response",
                ExternalMemoryRail.extractAssistantOutput(ctx(Map.of("result", Map.of("content", "assistant response")))));
    }

    @Test
    void resultMissing() {
        assertEquals("", ExternalMemoryRail.extractAssistantOutput(ctx(Map.of())));
    }

    @Test
    void resultWithUnknownKeys() {
        assertEquals("",
                ExternalMemoryRail.extractAssistantOutput(ctx(Map.of("result", Map.of("unknown", "value", "other", 123)))));
    }

    @Test
    void buildMemoryContext() {
        String block = ExternalMemoryRail.buildMemoryContextBlock("Previous conversation context");

        assertTrue(block.contains("<memory-context>"));
        assertTrue(block.contains("Previous conversation context"));
        assertTrue(block.contains("</memory-context>"));
        assertTrue(block.contains("NOT new user input"));
    }

    @Test
    void afterInvokeSkipsHeartbeatRuns() {
        RecordingProvider provider = new RecordingProvider();
        ExternalMemoryRail rail = initializedRail(provider);

        rail.afterInvoke(ctx(Map.of(
                "query", "health check",
                "result", Map.of("output", "healthy", "result_type", "answer"),
                "run_kind", RunKind.HEARTBEAT)));

        assertTrue(provider.syncTurnCalls.isEmpty());
    }

    @Test
    void afterInvokeSkipsCronRuns() {
        RecordingProvider provider = new RecordingProvider();
        ExternalMemoryRail rail = initializedRail(provider);

        rail.afterInvoke(ctx(Map.of(
                "query", "scheduled check",
                "result", Map.of("output", "ok", "result_type", "answer"),
                "run_kind", RunKind.CRON)));

        assertTrue(provider.syncTurnCalls.isEmpty());
    }

    @Test
    void afterInvokeSkipsEmptyAssistantOutput() {
        RecordingProvider provider = new RecordingProvider();
        ExternalMemoryRail rail = initializedRail(provider);

        rail.afterInvoke(ctx(Map.of(
                "query", "remember this",
                "result", Map.of("unknown", "value"),
                "run_kind", RunKind.NORMAL)));

        assertTrue(provider.syncTurnCalls.isEmpty());
    }

    private static ExternalMemoryRail initializedRail(RecordingProvider provider) {
        ExternalMemoryRail rail = new ExternalMemoryRail(provider);
        rail.init((DeepAgent) null);
        return rail;
    }

    private static CallbackContext ctx(Map<String, Object> values) {
        return new CallbackContext(null, values);
    }

    private static final class RecordingProvider extends MemoryProvider {
        private final List<Map<String, Object>> syncTurnCalls = new ArrayList<>();

        @Override
        public String getName() {
            return "mock_provider";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<Map<String, Object>> getToolSchemas() {
            return List.of(Map.of(
                    "name", "memory_search",
                    "description", "Search memory",
                    "parameters", Map.of("type", "object", "properties", Map.of())));
        }

        @Override
        public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
            return CompletableFuture.completedFuture("{\"result\":\"success\"}");
        }

        @Override
        public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture("Memory context for: " + query);
        }

        @Override
        public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
            syncTurnCalls.add(Map.of("user_msg", userMsg, "assistant_msg", assistantMsg, "kwargs", kwargs));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String systemPromptBlock() {
            return "Use memory_search tool.";
        }
    }
}
