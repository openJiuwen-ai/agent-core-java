/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Mirrors Python's launcher behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/pre_deployment_launcher.py}.
 */
class PreDeploymentLauncherTest {

    @Test
    void launchDefaultsToAioWithoutUsingIsolationKey() throws ExecutionException, InterruptedException {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        PreDeployLauncherConfig config = new PreDeployLauncherConfig("https://sandbox.example");
        assertThat(config.getSandboxType()).isEqualTo("aio");

        LaunchedSandbox sandbox = launcher.launch(config, 30, "ignored").get();

        assertThat(sandbox.baseUrl()).isEqualTo("https://sandbox.example");
        assertThat(sandbox.sandboxId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aio", "jiuwenbox"})
    void launchDoesNotUseIsolationKeyForExplicitRemoteType(String sandboxType) {
        SandboxLauncherConfig config = SandboxLauncherConfig.builder()
                .launcherType("pre_deploy")
                .sandboxType(sandboxType)
                .baseUrl("https://sandbox.example")
                .build();

        LaunchedSandbox sandbox = new PreDeploymentLauncher().launch(config, 30, "session:internal:key").join();

        assertThat(sandbox.sandboxId()).isNull();
    }

    @Test
    void launchUsesExplicitProviderSandboxId() throws Exception {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        PreDeployLauncherConfig config = new PreDeployLauncherConfig("https://sandbox.example");
        config.setSandboxType("jiuwenbox");
        config.setExtraParams(Map.of("sandbox_id", "existing-sandbox"));

        LaunchedSandbox sandbox = launcher.launch(config, 30, "session:internal:key").get();

        assertThat(sandbox.sandboxId()).isEqualTo("existing-sandbox");
    }

    @Test
    void launchAcceptsLegacySandboxLauncherConfigWithBaseUrl() throws Exception {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        SandboxLauncherConfig config = SandboxLauncherConfig.builder()
                .launcherType("pre_deploy")
                .baseUrl("http://local-provider:9999")
                .build();

        LaunchedSandbox sandbox = launcher.launch(config, 30, "demo").get();

        assertThat(sandbox.baseUrl()).isEqualTo("http://local-provider:9999");
        assertThat(sandbox.sandboxId()).isEqualTo("demo");
    }

    @Test
    void launchRejectsConfigWithoutBaseUrl() {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        SandboxLauncherConfig config = SandboxLauncherConfig.builder().launcherType("mock").build();

        assertThatThrownBy(() -> launcher.launch(config, 30, null).join())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base_url");
    }
}
