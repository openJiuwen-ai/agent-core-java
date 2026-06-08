package com.openjiuwen.core.sys_operation.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SysOperationConfigTest {

    @Test
    void localWorkDefaultsMatchPythonConfig() {
        LocalWorkConfig config = new LocalWorkConfig();

        assertThat(config.getShellAllowlist()).contains("rg", "python", "git");
        assertThat(config.isRestrictToSandbox()).isFalse();
        assertThat(config.getSandboxRoot()).isNull();
    }

    @Test
    void preDeployLauncherProvidesPythonDefaults() {
        PreDeployLauncherConfig config = new PreDeployLauncherConfig("http://sandbox.test");

        assertThat(config.getLauncherType()).isEqualTo("pre_deploy");
        assertThat(config.getSandboxType()).isEqualTo("aio");
        assertThat(config.getBaseUrl()).isEqualTo("http://sandbox.test");
    }
}
