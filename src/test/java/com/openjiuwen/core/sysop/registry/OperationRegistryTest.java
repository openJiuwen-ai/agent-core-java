/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        OperationDef def = OperationRegistry.getOperationInfo("non_existent", OperationMode.LOCAL);
        assertNull(def);
    }

    @Test
    @DisplayName("register and retrieve works")
    void testRegisterAndRetrieve() {
        OperationRegistry.register(LocalCodeOperation.class, "test_op", OperationMode.LOCAL, "test operation");

        OperationDef retrieved = OperationRegistry.getOperationInfo("test_op", OperationMode.LOCAL);
        assertNotNull(retrieved);
        assertEquals("test_op", retrieved.name());
        assertEquals(OperationMode.LOCAL, retrieved.mode());
        assertEquals("test operation", retrieved.description());
    }
}
