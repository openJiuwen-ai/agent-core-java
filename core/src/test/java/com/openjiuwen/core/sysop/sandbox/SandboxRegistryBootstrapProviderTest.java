/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.launchers.PreDeploymentLauncher;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.AioShellProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxCodeProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxFsProvider;
import com.openjiuwen.extensions.sys_operation.sandbox.providers.JiuwenBoxShellProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SandboxRegistryBootstrapProviderTest {
    private SandboxEndpoint endpoint;
    private SandboxGatewayConfig config;

    @BeforeEach
    void setUp() {
        SandboxRegistryBootstrap.ensureInitialized();
        endpoint = new SandboxEndpoint("http://localhost:8080", "sbx-test");
        config = SandboxGatewayConfig.builder().launcherConfig(SandboxLauncherConfig.builder()
                .launcherType("pre_deploy").baseUrl("http://localhost:8080").sandboxType("aio").build()).build();
    }

    @Test
    void testJiuwenBoxProvidersRegistered() {
        assertThat(SandboxRegistry.getProviderClass("jiuwenbox", "fs")).isEqualTo(JiuwenBoxFsProvider.class);
        assertThat(SandboxRegistry.getProviderClass("jiuwenbox", "shell")).isEqualTo(JiuwenBoxShellProvider.class);
        assertThat(SandboxRegistry.getProviderClass("jiuwenbox", "code")).isEqualTo(JiuwenBoxCodeProvider.class);
    }

    @Test
    void testAioProvidersRegistered() {
        assertThat(SandboxRegistry.getProviderClass("aio", "fs")).isEqualTo(AioFsProvider.class);
        assertThat(SandboxRegistry.getProviderClass("aio", "shell")).isEqualTo(AioShellProvider.class);
        assertThat(SandboxRegistry.getProviderClass("aio", "code")).isEqualTo(AioCodeProvider.class);
    }

    @Test
    void testLauncherRegistered() {
        assertThat(SandboxRegistry.getLauncher("pre_deploy")).isEqualTo(PreDeploymentLauncher.class);
    }

    @Test
    void testCreateJiuwenBoxProvider() {
        Object provider = SandboxRegistry.createProvider("jiuwenbox", "fs", endpoint, config);
        assertThat(provider).isInstanceOf(JiuwenBoxFsProvider.class);
    }

    @Test
    void testCreateAioProvider() {
        Object provider = SandboxRegistry.createProvider("aio", "fs", endpoint, config);
        assertThat(provider).isInstanceOf(AioFsProvider.class);
    }

    @Test
    void testUnknownProviderTypeThrows() {
        assertThatThrownBy(() -> SandboxRegistry.createProvider("unknown", "fs", endpoint, config))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("does not support operation");
    }
}
