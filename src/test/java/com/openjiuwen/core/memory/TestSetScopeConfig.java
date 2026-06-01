/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.memory.support.LongTermMemoryTestSupport;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;

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
            LongTermMemory mem = LongTermMemoryTestSupport.registeredMemory();

            MemoryScopeConfig scopeConfig = MemoryScopeConfig.builder()
                    .modelCfg(ModelRequestConfig.builder().modelName("test_model").build())
                    .modelClientCfg(ModelClientConfig.builder()
                            .clientProvider("DashScope")
                            .apiKey("test_api_key")
                            .apiBase("https://dashscope.aliyuncs.com/api/v1")
                            .build())
                    .embeddingCfg(new EmbeddingConfig(
                            "test_embedding_model",
                            "https://dashscope.aliyuncs.com/api/v1",
                            "test_api_key"))
                    .build();

            String scopeId = "test_scope_123";

            // Call setScopeConfig directly
            boolean result = mem.setScopeConfig(scopeId, scopeConfig);

            // Verify result
            assertTrue(result);

            // Verify config can be retrieved
            MemoryScopeConfig retrieved = mem.getScopeConfig(scopeId);
            assertNotNull(retrieved);
            assertEquals("test_model", retrieved.getModelCfg().getModelName());
            assertEquals("DashScope", retrieved.getModelClientCfg().getClientProvider());
            assertEquals("test_embedding_model", retrieved.getEmbeddingCfg().getModelName());
        }

        @Test
        @DisplayName("test set scope config returns false for invalid scope id")
        void testSetScopeConfigReturnsFalseForInvalidScopeId() {
            // Test that setScopeConfig returns false for invalid scope_id.
            LongTermMemory mem = LongTermMemoryTestSupport.registeredMemory();

            MemoryScopeConfig scopeConfig = new MemoryScopeConfig();

            // Invalid scope_id (empty)
            boolean result = mem.setScopeConfig("", scopeConfig);

            assertFalse(result);
        }

        @Test
        @DisplayName("test get scope config returns null when not set")
        void testGetScopeConfigReturnsNullWhenNotSet() {
            // Test that getScopeConfig returns null for non-existent scope.
            LongTermMemory mem = LongTermMemoryTestSupport.registeredMemory();

            // Get config for scope that was never set
            MemoryScopeConfig retrieved = mem.getScopeConfig("nonexistent_scope");

            assertNull(retrieved);
        }

        @Test
        @DisplayName("test set multiple scope configs")
        void testSetMultipleScopeConfigs() {
            // Test that multiple scope configs can be set.
            LongTermMemory mem = LongTermMemoryTestSupport.registeredMemory();

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
