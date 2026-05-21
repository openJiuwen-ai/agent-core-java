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
 * Unit tests for OpenJiuwenMemoryProvider.
 * <p>
 * Mirrors Python's test_openjiuwen_memory_provider.py from
 * <code>tests/unit_tests/core/memory/external/test_openjiuwen_memory_provider.py</code>.
 */
@DisplayName("OpenJiuwen Memory Provider Tests")
class TestOpenJiuwenMemoryProvider {

    @Nested
    @DisplayName("Name and Availability Tests")
    class TestNameAndAvailability {

        @Test
        @DisplayName("name returns openjiuwen")
        void testNameReturnsOpenjiuwen() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
            assertEquals("openjiuwen", provider.name());
        }

        @Test
        @DisplayName("is available returns true")
        void testIsAvailableReturnsTrue() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
            assertTrue(provider.isAvailable());
        }

        @Test
        @DisplayName("is available with embedding config")
        void testIsAvailableWithEmbeddingConfig() {
            Map<String, Object> config = new HashMap<>();
            Map<String, Object> embeddingConfig = new HashMap<>();
            embeddingConfig.put("model_name", "text-embedding-ada-002");
            config.put("embedding", embeddingConfig);

            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(config);
            assertTrue(provider.isAvailable());
        }

        @Test
        @DisplayName("is available with null config")
        void testIsAvailableWithNullConfig() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(null);
            assertTrue(provider.isAvailable());
        }
    }

    @Nested
    @DisplayName("Initialize Tests")
    class TestInitialize {

        @Test
        @DisplayName("initialize returns completed future")
        void testInitializeReturnsCompletedFuture() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
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
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
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
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
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
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
            Map<String, Object> args = new HashMap<>();

            CompletableFuture<String> result = provider.handleToolCall("ltm_search", args);

            assertNotNull(result);
        }

        @Test
        @DisplayName("get tool schemas returns list")
        void testGetToolSchemasReturnsList() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());

            var schemas = provider.getToolSchemas();

            assertNotNull(schemas);
        }
    }

    @Nested
    @DisplayName("MemoryProvider Interface Tests")
    class TestMemoryProviderInterface {

        @Test
        @DisplayName("OpenJiuwenMemoryProvider extends MemoryProvider")
        void testOpenJiuwenMemoryProviderExtendsMemoryProvider() {
            OpenJiuwenMemoryProvider provider = new OpenJiuwenMemoryProvider(new HashMap<>());
            assertTrue(provider instanceof MemoryProvider);
        }
    }
}