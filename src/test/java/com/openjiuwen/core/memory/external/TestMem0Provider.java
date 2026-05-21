/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Mem0MemoryProvider.
 * <p>
 * Mirrors Python's test_mem0_provider.py from
 * <code>tests/unit_tests/core/memory/external/test_mem0_provider.py</code>.
 */
@DisplayName("Mem0 Memory Provider Tests")
class TestMem0Provider {

    @Nested
    @DisplayName("Mem0MemoryProvider Construction Tests")
    class TestMem0MemoryProviderConstruction {

        @Test
        @DisplayName("provider can be created with api key")
        void testProviderCanBeCreatedWithApiKey() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            assertNotNull(provider);
        }

        @Test
        @DisplayName("provider name is mem0")
        void testProviderNameIsMem0() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            assertEquals("mem0", provider.name());
        }
    }

    @Nested
    @DisplayName("Availability Tests")
    class TestAvailability {

        @Test
        @DisplayName("is available with valid api key")
        void testIsAvailableWithValidApiKey() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            assertTrue(provider.isAvailable());
        }

        @Test
        @DisplayName("is not available with empty api key")
        void testIsNotAvailableWithEmptyApiKey() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("", "http://localhost");
            assertFalse(provider.isAvailable());
        }

        @Test
        @DisplayName("is not available with null api key")
        void testIsNotAvailableWithNullApiKey() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider(null, "http://localhost");
            assertFalse(provider.isAvailable());
        }
    }

    @Nested
    @DisplayName("Initialize Tests")
    class TestInitialize {

        @Test
        @DisplayName("initialize returns completed future")
        void testInitializeReturnsCompletedFuture() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            Map<String, Object> kwargs = new HashMap<>();

            CompletableFuture<Void> result = provider.initialize(kwargs);

            assertNotNull(result);
            assertTrue(result.isDone());
        }
    }

    @Nested
    @DisplayName("Prefetch Tests")
    class TestPrefetch {

        @Test
        @DisplayName("prefetch returns string")
        void testPrefetchReturnsString() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            Map<String, Object> kwargs = new HashMap<>();

            CompletableFuture<String> result = provider.prefetch("test query", kwargs);

            assertNotNull(result);
        }
    }

    @Nested
    @DisplayName("SyncTurn Tests")
    class TestSyncTurn {

        @Test
        @DisplayName("sync turn returns completed future")
        void testSyncTurnReturnsCompletedFuture() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            Map<String, Object> kwargs = new HashMap<>();

            CompletableFuture<Void> result = provider.syncTurn("user message", "assistant message", kwargs);

            assertNotNull(result);
            assertTrue(result.isDone());
        }
    }

    @Nested
    @DisplayName("ToolCall Tests")
    class TestToolCall {

        @Test
        @DisplayName("handle tool call returns string")
        void testHandleToolCallReturnsString() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            Map<String, Object> args = new HashMap<>();

            CompletableFuture<String> result = provider.handleToolCall("mem0_search", args);

            assertNotNull(result);
        }

        @Test
        @DisplayName("get tool schemas returns list")
        void testGetToolSchemasReturnsList() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");

            var schemas = provider.getToolSchemas();

            assertNotNull(schemas);
        }
    }

    @Nested
    @DisplayName("MemoryProvider Interface Tests")
    class TestMemoryProviderInterface {

        @Test
        @DisplayName("Mem0MemoryProvider extends MemoryProvider")
        void testMem0MemoryProviderExtendsMemoryProvider() {
            Mem0MemoryProvider provider = new Mem0MemoryProvider("test-key", "http://localhost");
            assertTrue(provider instanceof MemoryProvider);
        }
    }
}