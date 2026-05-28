/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.OperationRegistry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sandbox registry functionality.
 * <p>
 * Mirrors Python's {@code test_sandbox_registry.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_sandbox_registry.py}.
 */
class TestSandboxRegistry {

    @Test
    void testGetSupportedOperations() {
        /** Test that getSupportedOperations returns correct operations for SANDBOX mode. */
        List<String> ops = OperationRegistry.getSupportedOperations(OperationMode.SANDBOX);
        assertNotNull(ops);
        assertTrue(ops.contains("fs"));
        assertTrue(ops.contains("shell"));
        assertTrue(ops.contains("code"));
    }

    @Test
    void testGetSupportedOperationsLocal() {
        /** Test that getSupportedOperations returns correct operations for LOCAL mode. */
        List<String> ops = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
        assertNotNull(ops);
        assertTrue(ops.contains("fs"));
        assertTrue(ops.contains("shell"));
        assertTrue(ops.contains("code"));
    }
}