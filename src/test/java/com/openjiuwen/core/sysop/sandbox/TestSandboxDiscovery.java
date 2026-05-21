/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import com.openjiuwen.core.sysop.registry.OperationDef;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sandbox operation discovery via OperationRegistry.
 * <p>
 * Mirrors Python's {@code test_sandbox_discovery.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_sandbox_discovery.py}.
 */
class TestSandboxDiscovery {

    @Test
    void testSandboxDiscovery() {
        /** Test that Sandbox operations are correctly discovered via OperationRegistry. */
        OperationDef fsOp = OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX);
        assertNotNull(fsOp);
        assertEquals("fs", fsOp.getName());
        assertEquals(OperationMode.SANDBOX, fsOp.getMode());

        OperationDef shellOp = OperationRegistry.getOperationInfo("shell", OperationMode.SANDBOX);
        assertNotNull(shellOp);
        assertEquals("shell", shellOp.getName());

        OperationDef codeOp = OperationRegistry.getOperationInfo("code", OperationMode.SANDBOX);
        assertNotNull(codeOp);
        assertEquals("code", codeOp.getName());
    }
}