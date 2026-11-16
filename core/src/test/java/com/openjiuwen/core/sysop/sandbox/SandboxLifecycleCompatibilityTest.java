
package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxRecord;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxStatus;
import com.openjiuwen.extensions.sys_operation.sandbox.AioSandboxProfile;
import com.openjiuwen.extensions.sys_operation.sandbox.JiuwenBoxSandboxProfile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(manager.get("session-1")).isNull();
        assertThat(manager.getContainer("session-1")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aio", "jiuwenbox"})
    void remoteRecordsWithoutIdsAreEvictedByIsolationKey(String sandboxType) {
        SandboxGatewayConfig config = remoteConfig(sandboxType);
        ContainerManager manager = new ContainerManager();
        manager.acquire("expired", config);
        manager.store().get("expired").orElseThrow().setLastUsedTs(0);

        manager.acquire("live", config);

        assertThat(manager.store().get("expired")).isEmpty();
        assertThat(manager.get("expired")).isNull();
        assertThat(manager.getContainer("expired")).isNull();
        assertThat(manager.keys()).containsExactly("live");
        assertThat(manager.getContainer("live").getSandboxId()).isNull();
        assertThat(manager.release("live")).isTrue();
        assertThat(manager.size()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aio", "jiuwenbox"})
    void remoteEvictionRetainsOtherUnresolvedRecords(String sandboxType) {
        SandboxGatewayConfig config = remoteConfig(sandboxType);
        ContainerManager manager = new ContainerManager();
        manager.acquire("expired", config);
        manager.acquire("live", config);
        manager.store().get("expired").orElseThrow().setLastUsedTs(0);
        manager.store().get("live").orElseThrow().setLastUsedTs(100);

        assertThat(manager.evictExpired(config, 101)).hasSize(1);

        assertThat(manager.keys()).containsExactly("live");
        assertThat(manager.getContainer("live")).isNotNull();
        assertThat(manager.get("live")).isNotNull();
        assertThat(manager.getContainer("expired")).isNull();
        assertThat(manager.release("live", "pause")).isTrue();
    }

    @Test
    void evictionRemovesClientByIsolationKeyWhenPhysicalIdDiffers() {
        SandboxGatewayConfig config = remoteConfig("jiuwenbox");
        config.getLauncherConfig().setExtraParams(Map.of("sandbox_id", "physical-id"));
        ContainerManager manager = new ContainerManager();
        manager.acquire("isolation-key", config);
        manager.store().get("isolation-key").orElseThrow().setLastUsedTs(0);

        assertThat(manager.evictExpired(config, 101)).hasSize(1);

        assertThat(manager.size()).isZero();
        assertThat(manager.getContainer("isolation-key")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"aio", "jiuwenbox"})
    void legacyEndpointDoesNotInventAPhysicalSandboxId(String sandboxType) {
        SandboxGateway gateway = SandboxGateway.createForTest();

        GatewayResponse response = gateway.getSandbox(SandboxCreateRequest.builder()
                .config(remoteConfig(sandboxType)).isolationKey("internal:scope").build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isInstanceOfSatisfying(SandboxEndpoint.class, endpoint -> {
            assertThat(endpoint.baseUrl()).isEqualTo("http://localhost:8080");
            assertThat(endpoint.sandboxId()).isNull();
        });
        assertThat(gateway.disconnect("internal:scope")).isTrue();
    }

    private static SandboxGatewayConfig remoteConfig(String sandboxType) {
        SandboxGatewayConfig config = AioSandboxProfile.config("http://localhost:8080");
        if ("jiuwenbox".equals(sandboxType)) {
            config = JiuwenBoxSandboxProfile.config("http://localhost:8080");
        }
        config.getLauncherConfig().setIdleTtlSeconds(60);
        return config;
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
