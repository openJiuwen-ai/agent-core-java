/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.launchers;

import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxLauncherBaseTest {

    @Test
    void launchedSandboxDefaultsMatchPythonDataclass() {
        LaunchedSandbox sandbox = new LaunchedSandbox("http://sandbox");

        assertThat(sandbox.baseUrl()).isEqualTo("http://sandbox");
        assertThat(sandbox.sandboxId()).isNull();
        assertThat(sandbox.hostPort()).isNull();
    }

    @Test
    void baseLauncherDefaultsAreNoOps() throws ExecutionException, InterruptedException {
        SandboxLauncher launcher = new SandboxLauncher() {
            @Override
            public java.util.concurrent.CompletableFuture<LaunchedSandbox> launch(
                    SandboxLauncherConfig config,
                    int timeoutSeconds,
                    String isolationKey) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        new LaunchedSandbox("http://sandbox", isolationKey, timeoutSeconds));
            }
        };

        SandboxLauncherConfig config = SandboxLauncherConfig.builder()
                .launcherType("mock")
                .sandboxType("aio")
                .build();

        assertThat(launcher.launch(config, 30).get())
                .isEqualTo(new LaunchedSandbox("http://sandbox", null, 30));
        assertThat(launcher.launch(config, 10, "session-1").get())
                .isEqualTo(new LaunchedSandbox("http://sandbox", "session-1", 10));
        assertThat(launcher.pause("sandbox-1").get()).isNull();
        assertThat(launcher.resume("sandbox-1").get()).isNull();
        assertThat(launcher.delete("sandbox-1").get()).isNull();
        assertThat(launcher.checkStatus("sandbox-1").get()).isEqualTo(SandboxStatus.RUNNING);
    }
}
