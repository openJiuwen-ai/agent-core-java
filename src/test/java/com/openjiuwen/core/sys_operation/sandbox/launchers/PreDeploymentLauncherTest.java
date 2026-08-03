/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.launchers;

import com.openjiuwen.core.sys_operation.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
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

        assertThat(sandbox).isEqualTo(new LaunchedSandbox("https://sandbox.example"));
    }

    @Test
    void launchRejectsNonPreDeployConfig() {
        PreDeploymentLauncher launcher = new PreDeploymentLauncher();
        SandboxLauncherConfig config = SandboxLauncherConfig.builder().launcherType("mock").build();

        assertThatThrownBy(() -> launcher.launch(config, 30, null).join())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PreDeploymentLauncher requires PreDeployLauncherConfig");
    }
}
