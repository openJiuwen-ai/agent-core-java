
package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import org.junit.jupiter.api.Test;

import java.util.Map;

class SandboxGatewayConfigCompatibilityTest {
    @Test
    void gatewayConfigShouldExposePythonAlignedDefaults() {
        SandboxGatewayConfig config = SandboxGatewayConfig.builder().build();

        assertThat(config.getTimeoutSeconds()).isEqualTo(30);
        assertThat(config.getIsolation()).isNotNull();
        assertThat(config.getIsolation().getContainerScope()).isEqualTo(ContainerScope.SESSION);
        assertThat(config.getAuthHeaders()).isEmpty();
        assertThat(config.getAuthQueryParams()).isEmpty();
    }

    @Test
    void gatewayConfigShouldRetainLegacyAndNewLauncherFields() {
        SandboxGatewayConfig config = SandboxGatewayConfig.builder().gatewayUrl("http://localhost:9000")
                .params(Map.of("root_path", "/tmp/workspace")).timeoutSeconds(45)
                .isolation(SandboxIsolationConfig.builder().customId("session-1").containerScope(ContainerScope.CUSTOM)
                        .prefix("agent").build())
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                        .gatewayUrl("http://localhost:9000").baseUrl("http://localhost:9000").sandboxType("aio")
                        .extraParams(Map.of("sandbox_id", "sbx-1")).build())
                .build();

        assertThat(config.getGatewayUrl()).isEqualTo("http://localhost:9000");
        assertThat(config.getParams()).containsEntry("root_path", "/tmp/workspace");
        assertThat(config.getTimeoutSeconds()).isEqualTo(45);
        assertThat(config.getIsolation().getCustomId()).isEqualTo("session-1");
        assertThat(config.getIsolation().getContainerScope()).isEqualTo(ContainerScope.CUSTOM);
        assertThat(config.getLauncherConfig().getSandboxType()).isEqualTo("aio");
        assertThat(config.getLauncherConfig().getExtraParams()).containsEntry("sandbox_id", "sbx-1");
    }
}
