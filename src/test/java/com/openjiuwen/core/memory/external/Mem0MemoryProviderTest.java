/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests.unit_tests.core.memory.external.test_mem0_provider} in
 * {@code tests/unit_tests/core/memory/external/test_mem0_provider.py}.
 */
@DisplayName("Mem0 Memory Provider Tests")
class Mem0MemoryProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void testInitializeRequiresApiKey() {
        Mem0MemoryProvider provider = new Mem0MemoryProvider("");

        assertThrows(IllegalArgumentException.class, () -> provider.initialize(Map.of()).join());
    }

    @Test
    void testPrefetchDirectSearchForRailUsage() {
        FakeMem0Client fake = new FakeMem0Client();
        Mem0MemoryProvider provider = new Mem0MemoryProvider("k", "u1", "", false, fake);
        provider.initialize(Map.of()).join();

        String result = provider.prefetch("who am I", Map.of()).join();

        assertTrue(result.contains("## Mem0 Memory"));
        assertTrue(result.contains("- remember this"));
        assertEquals("who am I", fake.lastSearchKwargs.get("query"));
        assertEquals(Map.of("user_id", "u1"), fake.lastSearchKwargs.get("filters"));
    }

    @Test
    void testSyncTurnPushesUserAndAssistantMessages() {
        FakeMem0Client fake = new FakeMem0Client();
        Mem0MemoryProvider provider = new Mem0MemoryProvider("k", "u1", "a1", false, fake);
        provider.initialize(Map.of()).join();

        provider.syncTurn("u-msg", "a-msg", Map.of()).join();

        assertNotNull(fake.lastAddMessages);
        assertEquals("user", fake.lastAddMessages.get(0).get("role"));
        assertEquals("assistant", fake.lastAddMessages.get(1).get("role"));
        assertEquals("u1", fake.lastAddKwargs.get("user_id"));
        assertEquals("a1", fake.lastAddKwargs.get("agent_id"));
    }

    @Test
    void testHandleToolCallSearchAndConclude() throws Exception {
        FakeMem0Client fake = new FakeMem0Client();
        Mem0MemoryProvider provider = new Mem0MemoryProvider("k", "u1", "a1", false, fake);
        provider.initialize(Map.of()).join();

        Map<String, Object> searchData = json(provider.handleToolCall(
                "mem0_search", Map.of("query", "x", "top_k", 2)).join());
        assertTrue(searchData.containsKey("results"));
        assertEquals(1, searchData.get("count"));

        Map<String, Object> concludeData = json(provider.handleToolCall(
                "mem0_conclude", Map.of("conclusion", "new fact")).join());
        assertEquals("Fact stored.", concludeData.get("result"));
        assertEquals(Boolean.FALSE, fake.lastAddKwargs.get("infer"));
    }

    @Test
    void testShutdownCancelsPrefetchTask() {
        FakeMem0Client fake = new FakeMem0Client();
        Mem0MemoryProvider provider = new Mem0MemoryProvider("k", "", "", false, fake);
        provider.initialize(Map.of()).join();
        CompletableFuture<Void> task = new CompletableFuture<>();
        provider.setPrefetchTaskForTest(task);

        provider.shutdown().join();

        assertTrue(task.isCancelled());
        assertFalse(provider.isInitialized());
    }

    private static Map<String, Object> json(String payload) throws Exception {
        return MAPPER.readValue(payload, new TypeReference<>() {
        });
    }

    static class FakeMem0Client implements Mem0MemoryProvider.Mem0Client {
        Map<String, Object> lastSearchKwargs;
        List<Map<String, Object>> lastAddMessages;
        Map<String, Object> lastAddKwargs;

        @Override
        public Object search(Map<String, Object> kwargs) {
            lastSearchKwargs = new LinkedHashMap<>(kwargs);
            return Map.of("results", List.of(Map.of("memory", "remember this", "score", 0.9)));
        }

        @Override
        public Object add(List<Map<String, Object>> messages, Map<String, Object> kwargs) {
            lastAddMessages = List.copyOf(messages);
            lastAddKwargs = new LinkedHashMap<>(kwargs);
            return Map.of("ok", true);
        }

        @Override
        public Object getAll(Map<String, Object> kwargs) {
            return Map.of("results", List.of(Map.of("memory", "hello"), Map.of("memory", "world")));
        }
    }
}
