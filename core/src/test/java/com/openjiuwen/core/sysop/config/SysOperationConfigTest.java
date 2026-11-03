package com.openjiuwen.core.sysop.config;

import org.junit.jupiter.api.Test;

import java.util.List;

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
    void localWorkConfigRetainsFourArgumentConstructor() {
        LocalWorkConfig config = new LocalWorkConfig(
                List.of("python"),
                List.of("workspace"),
                true,
                List.of("dangerous"));

        assertThat(config.getShellAllowlist()).containsExactly("python");
        assertThat(config.getSandboxRoot()).containsExactly("workspace");
        assertThat(config.isRestrictToSandbox()).isTrue();
        assertThat(config.getDangerousPatterns()).containsExactly("dangerous");
        assertThat(config.getWorkDir()).isNull();
    }

    @Test
    void preDeployLauncherProvidesPythonDefaults() {
        PreDeployLauncherConfig config = new PreDeployLauncherConfig("http://sandbox.test");

        assertThat(config.getLauncherType()).isEqualTo("pre_deploy");
        assertThat(config.getSandboxType()).isEqualTo("aio");
        assertThat(config.getBaseUrl()).isEqualTo("http://sandbox.test");
    }
}
