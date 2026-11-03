
package com.openjiuwen.extensions.sys_operation.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SandboxProfileCompatibilityTest {
    @Test
    void aioProfileShouldBuildGatewayConfigThroughLauncherConfig() {
        SandboxGatewayConfig config = AioSandboxProfile.config("http://localhost:8080", Map.of("timeout_seconds", 30));

        assertThat(config.getGatewayUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getParams()).isEmpty();
        assertThat(config.getLauncherConfig()).isNotNull();
        assertThat(config.getLauncherConfig().getLauncherType()).isEqualTo("pre_deploy");
        assertThat(config.getLauncherConfig().getSandboxType()).isEqualTo("aio");
        assertThat(config.getLauncherConfig().getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.getLauncherConfig().getExtraParams()).containsEntry("timeout_seconds", 30);
    }

    @Test
    void jiuwenBoxProfileShouldBuildGatewayConfigThroughLauncherConfig() {
        SandboxGatewayConfig config =
            JiuwenBoxSandboxProfile.config("http://localhost:8321", Map.of("sandbox_id", "sbx-1"));

        assertThat(config.getGatewayUrl()).isEqualTo("http://localhost:8321");
        assertThat(config.getParams()).isEmpty();
        assertThat(config.getLauncherConfig()).isNotNull();
        assertThat(config.getLauncherConfig().getLauncherType()).isEqualTo("pre_deploy");
        assertThat(config.getLauncherConfig().getSandboxType()).isEqualTo("jiuwenbox");
        assertThat(config.getLauncherConfig().getBaseUrl()).isEqualTo("http://localhost:8321");
        assertThat(config.getLauncherConfig().getExtraParams()).containsEntry("sandbox_id", "sbx-1");
    }
}
