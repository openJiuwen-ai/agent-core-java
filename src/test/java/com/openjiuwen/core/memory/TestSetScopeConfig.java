/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.config.MemoryEngineConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SetScopeConfig.
 * Mirrors Python's tests/unit_tests/core/memory/test_set_scope_config.py
 */
class TestSetScopeConfig {

    @BeforeEach
    void setUp() {
        LongTermMemory.resetInstance();
    }

    @AfterEach
    void tearDown() {
        LongTermMemory.resetInstance();
    }

    @Nested
    @DisplayName("SetScopeConfig tests")
    class ConfigTests {

        @Test
        @DisplayName("test set scope config without set config")
        void testSetScopeConfigWithoutSetConfig() {
            // Test that setScopeConfig works after registerStore.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();

            // Register store first - this automatically calls setConfig
            mem.registerStore(kvStore, null, null, null);

            // Create a simple MemoryScopeConfig
            MemoryScopeConfig scopeConfig = new MemoryScopeConfig();

            String scopeId = "test_scope_123";

            // Call setScopeConfig directly
            boolean result = mem.setScopeConfig(scopeId, scopeConfig);

            // Verify result
            assertTrue(result);

            // Verify config can be retrieved
            MemoryScopeConfig retrieved = mem.getScopeConfig(scopeId);
            assertNotNull(retrieved);
        }

        @Test
        @DisplayName("test set scope config returns false for invalid scope id")
        void testSetScopeConfigReturnsFalseForInvalidScopeId() {
            // Test that setScopeConfig returns false for invalid scope_id.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();

            mem.registerStore(kvStore, null, null, null);

            MemoryScopeConfig scopeConfig = new MemoryScopeConfig();

            // Invalid scope_id (empty)
            boolean result = mem.setScopeConfig("", scopeConfig);

            assertFalse(result);
        }

        @Test
        @DisplayName("test get scope config returns null when not set")
        void testGetScopeConfigReturnsNullWhenNotSet() {
            // Test that getScopeConfig returns null for non-existent scope.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();

            mem.registerStore(kvStore, null, null, null);

            // Get config for scope that was never set
            MemoryScopeConfig retrieved = mem.getScopeConfig("nonexistent_scope");

            assertNull(retrieved);
        }

        @Test
        @DisplayName("test set multiple scope configs")
        void testSetMultipleScopeConfigs() {
            // Test that multiple scope configs can be set.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();

            mem.registerStore(kvStore, null, null, null);

            String scope1 = "scope_1";
            String scope2 = "scope_2";

            MemoryScopeConfig config1 = new MemoryScopeConfig();
            MemoryScopeConfig config2 = new MemoryScopeConfig();

            // Set multiple configs
            assertTrue(mem.setScopeConfig(scope1, config1));
            assertTrue(mem.setScopeConfig(scope2, config2));

            // Verify both can be retrieved
            assertNotNull(mem.getScopeConfig(scope1));
            assertNotNull(mem.getScopeConfig(scope2));
        }

        @Test
        @DisplayName("test scope config key constant")
        void testScopeConfigKeyConstant() {
            // Test that SCOPE_CONFIG_KEY constant is defined correctly.
            assertEquals("memory_scope_config", LongTermMemory.SCOPE_CONFIG_KEY);
        }
    }
}