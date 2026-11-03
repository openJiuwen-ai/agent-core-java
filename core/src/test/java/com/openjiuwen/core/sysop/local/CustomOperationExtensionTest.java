/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import static org.junit.jupiter.api.Assertions.*;

import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;

import org.junit.jupiter.api.*;

import java.util.List;

/**
 * Tests for custom operation extension and registry.
 * Mirrors Python's test_custom_operation_extension.py.
 */
class CustomOperationExtensionTest {
    // ==================== Multi-mode coexistence ====================
    @Test
    @DisplayName("Built-in FS for LOCAL and SANDBOX modes coexist in registry")
    void testMultiModeFsCoexistence() {
        OperationDef localFs = OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL);
        OperationDef sandboxFs = OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX);

        assertNotNull(localFs, "LOCAL fs should be registered");
        assertNotNull(sandboxFs, "SANDBOX fs should be registered");

        assertEquals(OperationMode.LOCAL, localFs.mode());
        assertEquals(OperationMode.SANDBOX, sandboxFs.mode());
    }

    @Test
    @DisplayName("getSupportedOperations returns built-in operations for both modes")
    void testSupportedOperationsBothModes() {
        List<String> localOps = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
        List<String> sandboxOps = OperationRegistry.getSupportedOperations(OperationMode.SANDBOX);

        assertTrue(localOps.contains("fs"), "LOCAL should have fs");
        assertTrue(localOps.contains("shell"), "LOCAL should have shell");
        assertTrue(localOps.contains("code"), "LOCAL should have code");

        assertTrue(sandboxOps.contains("fs"), "SANDBOX should have fs");
        assertTrue(sandboxOps.contains("shell"), "SANDBOX should have shell");
        assertTrue(sandboxOps.contains("code"), "SANDBOX should have code");
    }

    @Test
    @DisplayName("Built-in operations for LOCAL have correct mode")
    void testLocalOperationsModes() {
        String[] names = {"fs", "shell", "code"};
        for (String name : names) {
            OperationDef def = OperationRegistry.getOperationInfo(name, OperationMode.LOCAL);
            assertNotNull(def, name + " should be registered for LOCAL");
            assertEquals(OperationMode.LOCAL, def.mode(), name + " should be LOCAL mode");
        }
    }

    @Test
    @DisplayName("Built-in operations for SANDBOX have correct mode")
    void testSandboxOperationsModes() {
        String[] names = {"fs", "shell", "code"};
        for (String name : names) {
            OperationDef def = OperationRegistry.getOperationInfo(name, OperationMode.SANDBOX);
            assertNotNull(def, name + " should be registered for SANDBOX");
            assertEquals(OperationMode.SANDBOX, def.mode(), name + " should be SANDBOX mode");
        }
    }

    @Test
    @DisplayName("Non-existent operation returns empty")
    void testNonExistentOperation() {
        OperationDef def = OperationRegistry.getOperationInfo("nonexistent", OperationMode.LOCAL);
        assertNull(def);
    }

    @Test
    @DisplayName("Register and retrieve custom operation")
    void testRegisterCustomOperation() {
        // Register a custom operation using LocalCodeOperation as a placeholder class
        OperationRegistry.register(LocalCodeOperation.class, "custom_test_op", OperationMode.LOCAL,
                "Custom test operation");

        OperationDef retrieved = OperationRegistry.getOperationInfo("custom_test_op", OperationMode.LOCAL);
        assertNotNull(retrieved);
        assertEquals("custom_test_op", retrieved.name());
        assertEquals(OperationMode.LOCAL, retrieved.mode());
        assertEquals("Custom test operation", retrieved.description());
    }
}
