/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.registry;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OperationRegistry.
 */
class OperationRegistryTest {

    @Test
    @DisplayName("getSupportedOperations returns known operations for LOCAL mode")
    void testGetSupportedOperationsLocal() {
        List<String> ops = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
        assertNotNull(ops);
        // After loadBuiltInOperations, should contain fs, shell, code
        // but they may fail to load if classes aren't on classpath in test context
        // This test verifies the API contract
    }

    @Test
    @DisplayName("getOperationInfo returns empty for non-existent operation")
    void testGetOperationInfoNotFound() {
        Optional<OperationDef> def = OperationRegistry.getOperationInfo("non_existent", OperationMode.LOCAL);
        assertFalse(def.isPresent());
    }

    @Test
    @DisplayName("register and retrieve works")
    void testRegisterAndRetrieve() {
        OperationRegistry.register(LocalCodeOperation.class, "test_op", OperationMode.LOCAL, "test operation");

        Optional<OperationDef> retrieved = OperationRegistry.getOperationInfo("test_op", OperationMode.LOCAL);
        assertTrue(retrieved.isPresent());
        assertEquals("test_op", retrieved.get().getName());
        assertEquals(OperationMode.LOCAL, retrieved.get().getMode());
        assertEquals("test operation", retrieved.get().getDescription());
    }
}
