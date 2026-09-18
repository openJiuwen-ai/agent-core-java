/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's launcher behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/launchers/pre_deployment_launcher.py}.
 */
class PreDeploymentLauncherTest {

    @Test
    void launchReturnsConfiguredBaseUrl() throws ExecutionException, InterruptedException {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        PreDeployLauncherConfig config = new PreDeployLauncherConfig("https://sandbox.example");

        LaunchedSandbox sandbox = launcher.launch(config, 30, "ignored").get();

        assertThat(sandbox.baseUrl()).isEqualTo("https://sandbox.example");
        assertThat(sandbox.sandboxId()).isEqualTo("ignored");
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
