package com.openjiuwen.core.memory.external;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemoryProviderTest {

    @Test
    void defaultHooksMirrorPythonBaseClass() {
        StubMemoryProvider provider = new StubMemoryProvider();

        assertEquals("stub", provider.getName());
        assertFalse(provider.isAvailable());
        assertEquals("", provider.systemPromptBlock());
        assertFalse(provider.isInitialized());
        assertNull(provider.shutdown().join());
        assertNull(provider.onSessionEnd(List.of(Map.of("role", "user"))).join());
        assertNull(provider.initialize().join());
        assertEquals("prefetch:q", provider.prefetch("q").join());
        assertNull(provider.syncTurn("u", "a").join());
    }

    private static final class StubMemoryProvider extends MemoryProvider {

        @Override
        public String getName() {
            return "stub";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public CompletableFuture<Void> initialize(Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<Map<String, Object>> getToolSchemas() {
            return List.of(Map.of("name", "tool"));
        }

        @Override
        public CompletableFuture<String> handleToolCall(String toolName, Map<String, Object> args) {
            return CompletableFuture.completedFuture(toolName + ":" + args.size());
        }

        @Override
        public CompletableFuture<String> prefetch(String query, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture("prefetch:" + query);
        }

        @Override
        public CompletableFuture<Void> syncTurn(String userMsg, String assistantMsg, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
