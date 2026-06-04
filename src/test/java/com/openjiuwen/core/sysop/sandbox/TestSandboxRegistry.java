/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test sandbox registry functionality.
 * <p>
 * Mirrors Python's {@code test_sandbox_registry.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_sandbox_registry.py}.
 */
class TestSandboxRegistry {

    @Test
    void testRegisterAndCreateLauncher() {
        String name = "_test_registry_launcher";
        SandboxRegistry.registerLauncher(name, DummyLauncher::new);
        try {
            SandboxLauncher launcher = SandboxRegistry.createLauncher(name);
            assertInstanceOf(DummyLauncher.class, launcher);
        } finally {
            SandboxRegistry.unregisterLauncher(name);
        }
    }

    @Test
    void testRegisterAndCreateProvider() {
        String sandboxType = "_test_registry_sandbox";
        String opType = "fs";
        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://localhost:8080")
                .build();
        PreDeployLauncherConfig config = PreDeployLauncherConfig.create("http://localhost:8080", sandboxType);

        SandboxRegistry.registerProvider(sandboxType, opType, DummyProvider::new);
        try {
            Object provider = SandboxRegistry.createProvider(sandboxType, opType, endpoint, config);
            assertInstanceOf(DummyProvider.class, provider);
            DummyProvider dummy = (DummyProvider) provider;
            assertEquals(endpoint, dummy.endpoint);
            assertEquals(config, dummy.config);
        } finally {
            SandboxRegistry.unregisterProvider(sandboxType, opType);
        }
    }

    @Test
    void testCreateLauncherUnknownTypeRaises() {
        IllegalArgumentException err = assertThrows(IllegalArgumentException.class,
                () -> SandboxRegistry.createLauncher("_missing_launcher"));
        assertTrue(err.getMessage().contains("Unknown launcher_type"));
    }

    @Test
    void testCreateProviderUnknownTypeRaises() {
        UnsupportedOperationException err = assertThrows(UnsupportedOperationException.class,
                () -> SandboxRegistry.createProvider(
                        "_missing_sandbox",
                        "fs",
                        SandboxEndpoint.builder().baseUrl("http://localhost:8080").build(),
                        null));
        assertTrue(err.getMessage().contains("No provider registered"));
    }

    private static class DummyLauncher extends SandboxLauncher {
        @Override
        public LaunchedSandbox launch(Object config, int timeoutSeconds, String isolationKey) {
            return LaunchedSandbox.builder()
                    .baseUrl("http://localhost:8080")
                    .sandboxId("dummy")
                    .build();
        }
    }

    private static class DummyProvider {
        private final SandboxEndpoint endpoint;
        private final Object config;

        private DummyProvider(SandboxEndpoint endpoint, Object config) {
            this.endpoint = endpoint;
            this.config = config;
        }
    }
}
