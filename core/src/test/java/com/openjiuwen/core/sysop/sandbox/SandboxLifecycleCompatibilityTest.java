
package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxRecord;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

class SandboxLifecycleCompatibilityTest {
    @TempDir
    Path tempDir;

    private SandboxGatewayConfig config(Integer idleTtlSeconds) {
        return SandboxGatewayConfig.builder().params(Map.of("root_path", tempDir.toString()))
                .isolation(SandboxIsolationConfig.builder().customId("session-1").build())
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                        .baseUrl("http://localhost:8080").gatewayUrl("http://localhost:8080").sandboxType("local")
                        .idleTtlSeconds(idleTtlSeconds).build())
                .build();
    }

    @Test
    void containerManagerShouldPersistLifecycleRecord() {
        SandboxTestLocalProviders.ensureRegistered();
        ContainerManager manager = new ContainerManager();

        SandboxClient first = manager.acquire(null, config(60));
        SandboxClient second = manager.acquire(null, config(60));
        SandboxRecord record = manager.store().get("session-1").orElse(null);

        assertThat(first).isSameAs(second);
        assertThat(record).isNotNull();
        assertThat(record.getSandboxType()).isEqualTo("local");
        assertThat(record.getStatus()).isEqualTo(SandboxStatus.RUNNING);
        assertThat(manager.getContainer("session-1").getBaseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void containerManagerShouldEvictExpiredRecords() {
        SandboxTestLocalProviders.ensureRegistered();
        ContainerManager manager = new ContainerManager();
        manager.acquire(null, config(1));

        assertThat(manager.evictExpired(config(1), (System.currentTimeMillis() / 1000.0) + 5.0)).hasSize(1);
        assertThat(manager.store().get("session-1")).isEmpty();
    }

    @Test
    void gatewayShouldReleaseWithPauseOrKeepSemantics() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxGateway gateway = SandboxGateway.createForTest();
        gateway.connect("keep-demo", config(60));
        gateway.connect("pause-demo", config(60));

        assertThat(gateway.releaseSandbox("keep-demo", "keep").isSuccess()).isTrue();
        assertThat(gateway.releaseSandbox("pause-demo", "pause").isSuccess()).isTrue();
    }
}
