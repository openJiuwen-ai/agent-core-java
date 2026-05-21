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
 * Unit tests for OpenVikingMemoryProvider.
 * <p>
 * Mirrors Python's test_openviking_memory_provider.py from
 * <code>tests/unit_tests/core/memory/external/test_openviking_memory_provider.py</code>.
 */
@DisplayName("OpenViking Memory Provider Tests")
class TestOpenVikingMemoryProvider {

    @Nested
    @DisplayName("Name and Availability Tests")
    class TestNameAndAvailability {

        @Test
        @DisplayName("provider can be created")
        void testProviderCanBeCreated() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
            assertNotNull(provider);
        }

        @Test
        @DisplayName("provider name is openviking")
        void testProviderNameIsOpenviking() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
            assertEquals("openviking", provider.name());
        }

        @Test
        @DisplayName("provider is available")
        void testProviderIsAvailable() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
            assertTrue(provider.isAvailable());
        }
    }

    @Nested
    @DisplayName("Initialize Tests")
    class TestInitialize {

        @Test
        @DisplayName("initialize returns completed future")
        void testInitializeReturnsCompletedFuture() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
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
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
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
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
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
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
            Map<String, Object> args = new HashMap<>();

            CompletableFuture<String> result = provider.handleToolCall("viking_search", args);

            assertNotNull(result);
        }

        @Test
        @DisplayName("get tool schemas returns list")
        void testGetToolSchemasReturnsList() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());

            var schemas = provider.getToolSchemas();

            assertNotNull(schemas);
        }
    }

    @Nested
    @DisplayName("MemoryProvider Interface Tests")
    class TestMemoryProviderInterface {

        @Test
        @DisplayName("OpenVikingMemoryProvider extends MemoryProvider")
        void testOpenVikingMemoryProviderExtendsMemoryProvider() {
            OpenVikingMemoryProvider provider = new OpenVikingMemoryProvider(new HashMap<>());
            assertTrue(provider instanceof MemoryProvider);
        }
    }
}