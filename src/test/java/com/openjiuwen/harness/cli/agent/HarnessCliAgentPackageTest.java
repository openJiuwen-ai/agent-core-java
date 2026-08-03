/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code openjiuwen.harness.cli.agent} module in
 * {@code openjiuwen/harness/cli/agent/__init__.py}.
 */
class HarnessCliAgentPackageTest {

    @Test
    void allShouldMatchPythonExportOrder() {
        assertThat(HarnessCliAgentPackage.all()).containsExactly(
                "CLIConfig",
                "load_config",
                "AgentBackend",
                "LocalBackend",
                "create_agent",
                "create_backend"
        );
    }

    @Test
    void getAttrShouldResolveLazyFactoryExports() {
        assertThat(HarnessCliAgentPackage.getAttr("CLIConfig").ownerType()).isEqualTo(CliAgentConfig.class);
        assertThat(HarnessCliAgentPackage.getAttr("load_config").ownerType()).isEqualTo(CliAgentConfig.class);
        assertThat(HarnessCliAgentPackage.getAttr("AgentBackend").ownerType()).isEqualTo(AgentBackend.class);
        assertThat(HarnessCliAgentPackage.getAttr("LocalBackend").ownerType()).isEqualTo(LocalBackend.class);
        assertThat(HarnessCliAgentPackage.getAttr("create_agent").ownerType()).isEqualTo(CliAgentFactory.class);
        assertThat(HarnessCliAgentPackage.getAttr("create_backend").ownerType()).isEqualTo(CliAgentFactory.class);
    }

    @Test
    void getAttrShouldRejectUnknownSymbol() {
        assertThatThrownBy(() -> HarnessCliAgentPackage.getAttr("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no attribute 'missing'");
    }
}
