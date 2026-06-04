/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.support.LongTermMemoryTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MemoryException handling in LongTermMemory.
 *
 * <p>Mirrors Python's tests/unit_tests/core/memory/test_memory_exception.py.</p>
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
            LongTermMemory mem = LongTermMemory.getInstance();

            BaseError ex = assertThrows(BaseError.class, () -> mem.registerStore(null, null, null, null));

            assertEquals(StatusCode.MEMORY_REGISTER_STORE_EXECUTION_ERROR, ex.getStatus());
        }

        @Test
        @DisplayName("test register store kv store required")
        void testRegisterStoreKvStoreRequired() {
            LongTermMemory mem = LongTermMemory.getInstance();

            BaseError ex = assertThrows(BaseError.class, () -> mem.registerStore(null, null, null, null));

            assertTrue(ex.getMessage().contains("kv store")
                    || ex.getMessage().contains("kv_store")
                    || ex.getMessage().contains("required"));
        }

        @Test
        @DisplayName("test register store with valid stores")
        void testRegisterStoreWithValidStores() {
            LongTermMemoryTestSupport.registeredMemory();
        }

        @Test
        @DisplayName("test singleton pattern")
        void testSingletonPattern() {
            LongTermMemory instance1 = LongTermMemory.getInstance();
            LongTermMemory instance2 = LongTermMemory.getInstance();

            assertSame(instance1, instance2);
        }

        @Test
        @DisplayName("test reset instance")
        void testResetInstance() {
            LongTermMemory instance1 = LongTermMemory.getInstance();
            LongTermMemory.resetInstance();
            LongTermMemory instance2 = LongTermMemory.getInstance();

            assertNotSame(instance1, instance2);
        }
    }
}
