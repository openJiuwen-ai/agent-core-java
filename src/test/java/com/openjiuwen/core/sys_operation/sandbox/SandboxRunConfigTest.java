/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxIsolationConfig;
import org.junit.jupiter.api.Test;

class SandboxRunConfigTest {

    @Test
    void builderRetainsGatewayConfigAndIsolationTemplate() {
        SandboxGatewayConfig gatewayConfig = SandboxGatewayConfig.builder()
                .isolation(new SandboxIsolationConfig())
                .timeoutSeconds(45)
                .build();

        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(gatewayConfig)
                .isolationKeyTemplate("team-{session_id}")
                .build();

        assertThat(runConfig.getConfig()).isSameAs(gatewayConfig);
        assertThat(runConfig.getConfig().getTimeoutSeconds()).isEqualTo(45);
        assertThat(runConfig.getIsolationKeyTemplate()).isEqualTo("team-{session_id}");
    }
}
