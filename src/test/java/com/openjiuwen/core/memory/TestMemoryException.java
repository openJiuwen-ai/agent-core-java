/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemoryException handling in LongTermMemory.
 * Mirrors Python's tests/unit_tests/core/memory/test_memory_exception.py
 */
class TestMemoryException {

    @BeforeEach
    void setUp() {
        LongTermMemory.resetInstance();
    }

    @AfterEach
    void tearDown() {
        LongTermMemory.resetInstance();
    }

    @Nested
    @DisplayName("MemoryException tests")
    class ExceptionTests {

        @Test
        @DisplayName("test register store kv store null")
        void testRegisterStoreKvStoreNull() {
            // Test that register_store throws error when kv_store is null.
            LongTermMemory mem = LongTermMemory.getInstance();

            BaseError ex = assertThrows(BaseError.class, () -> {
                mem.registerStore(null, null, null, null);
            });

            assertEquals(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR, ex.getStatus());
        }

        @Test
        @DisplayName("test register store kv store required")
        void testRegisterStoreKvStoreRequired() {
            // Test that kv store is required, other stores can be null.
            LongTermMemory mem = LongTermMemory.getInstance();

            // Should throw because kv_store is null
            BaseError ex = assertThrows(BaseError.class, () -> {
                mem.registerStore(null, null, null, null);
            });

            assertTrue(ex.getMessage().contains("kv store") || 
                       ex.getMessage().contains("kv_store") ||
                       ex.getMessage().contains("required"));
        }

        @Test
        @DisplayName("test register store with valid kv store")
        void testRegisterStoreWithValidKvStore() {
            // Test that register_store succeeds with valid kv_store.
            LongTermMemory mem = LongTermMemory.getInstance();
            InMemoryKVStore kvStore = new InMemoryKVStore();

            // Should succeed - kv_store is provided
            // Note: Other stores can be null
            mem.registerStore(kvStore, null, null, null);

            // No exception thrown means success
        }

        @Test
        @DisplayName("test singleton pattern")
        void testSingletonPattern() {
            // Test that LongTermMemory follows singleton pattern.
            LongTermMemory instance1 = LongTermMemory.getInstance();
            LongTermMemory instance2 = LongTermMemory.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("test reset instance")
        void testResetInstance() {
            // Test that resetInstance creates new instance.
            LongTermMemory instance1 = LongTermMemory.getInstance();
            LongTermMemory.resetInstance();
            LongTermMemory instance2 = LongTermMemory.getInstance();

            assertNotSame(instance1, instance2);
        }
    }
}