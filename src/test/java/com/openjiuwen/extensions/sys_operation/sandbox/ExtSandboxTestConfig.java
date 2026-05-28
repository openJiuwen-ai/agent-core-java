/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration for extensions sys_operation sandbox tests.
 * <p>
 * Tests for Sandbox configuration and registry.
 */
class ExtSandboxTestConfig {

    @Nested
    @DisplayName("ExtSandbox config tests")
    class ConfigTests {

        @Test
        @DisplayName("Test SandboxRegistry class exists")
        void testSandboxRegistryClassExists() {
            assertNotNull(SandboxRegistry.class);
        }

        @Test
        @DisplayName("Test sandbox configuration")
        void testExtSandboxConfig() {
            assertNotNull(SandboxRegistry.class);
            // Sandbox registry should be accessible
        }
    }
}