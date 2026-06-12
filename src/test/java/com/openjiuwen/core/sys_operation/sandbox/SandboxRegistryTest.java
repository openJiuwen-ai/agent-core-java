/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sys_operation.sandbox.launchers.SandboxLauncher;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxStatus;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code SandboxRegistry} in
 * {@code openjiuwen/core/sys_operation/sandbox/sandbox_registry.py}.
 */
class SandboxRegistryTest {

    @Test
    void registerAndCreateLauncher() {
        SandboxRegistry.registerLauncher("unit-launcher", TestLauncher.class);

        SandboxLauncher launcher = SandboxRegistry.createLauncher("unit-launcher");

        assertInstanceOf(TestLauncher.class, launcher);
        SandboxRegistry.unregisterLauncher("unit-launcher");
        assertNull(SandboxRegistry.getLauncherCls("unit-launcher"));
    }

    @Test
    void registerAndCreateProvider() {
        SandboxRegistry.registerProvider("unit-sandbox", "shell", TestProvider.class);

        Object provider = SandboxRegistry.createProvider(
                "unit-sandbox",
                "shell",
                new SandboxEndpoint("http://sandbox", "sb-1"),
                new SandboxGatewayConfig());

        assertInstanceOf(TestProvider.class, provider);
        TestProvider typed = (TestProvider) provider;
        assertEquals("http://sandbox", typed.endpoint.baseUrl());
        SandboxRegistry.unregisterProvider("unit-sandbox", "shell");
        assertNull(SandboxRegistry.getProviderCls("unit-sandbox", "shell"));
    }

    static final class TestLauncher extends SandboxLauncher {
        @Override
        public CompletableFuture<LaunchedSandbox> launch(
                com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig config,
                int timeoutSeconds,
                String isolationKey) {
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox", "sb-1"));
        }

        @Override
        public CompletableFuture<SandboxStatus> checkStatus(String sandboxId) {
            return CompletableFuture.completedFuture(SandboxStatus.RUNNING);
        }
    }

    static final class TestProvider {
        private final SandboxEndpoint endpoint;

        TestProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            this.endpoint = endpoint;
        }
    }
}
