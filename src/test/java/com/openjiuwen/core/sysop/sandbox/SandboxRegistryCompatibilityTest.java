
package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import org.junit.jupiter.api.Test;

class SandboxRegistryCompatibilityTest {
    public static final class DummyLauncher implements SandboxLauncher {
        @Override
        public LaunchedSandbox launch(SandboxLauncherConfig config, int timeoutSeconds, String isolationKey) {
            return null;
        }
    }

    public static final class DummyProvider {
        private final SandboxEndpoint endpoint;
        private final SandboxGatewayConfig config;

        public DummyProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            this.endpoint = endpoint;
            this.config = config;
        }
    }

    @Test
    void registryShouldRegisterAndCreateLauncher() {
        String name = "_test_registry_launcher";
        SandboxRegistry.registerLauncher(name, DummyLauncher.class);
        try {
            SandboxLauncher launcher = SandboxRegistry.createLauncher(name);
            assertThat(launcher).isInstanceOf(DummyLauncher.class);
        } finally {
            SandboxRegistry.unregisterLauncher(name);
        }
    }

    @Test
    void registryShouldRegisterAndCreateProvider() {
        String sandboxType = "_test_registry_sandbox";
        String operationType = "fs";
        SandboxEndpoint endpoint = SandboxEndpoint.builder().baseUrl("http://localhost:8080").build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder().launcherConfig(SandboxLauncherConfig.builder()
                .launcherType("pre_deploy").baseUrl("http://localhost:8080").sandboxType("aio").build()).build();

        SandboxRegistry.registerProvider(sandboxType, operationType, DummyProvider.class);
        try {
            Object provider = SandboxRegistry.createProvider(sandboxType, operationType, endpoint, config);
            assertThat(provider).isInstanceOf(DummyProvider.class);
            DummyProvider typed = (DummyProvider) provider;
            assertThat(typed.endpoint).isEqualTo(endpoint);
            assertThat(typed.config).isEqualTo(config);
        } finally {
            SandboxRegistry.unregisterProvider(sandboxType, operationType);
        }
    }

    @Test
    void registryBootstrapShouldRegisterBuiltinPreDeployLauncherOnly() {
        SandboxRegistryBootstrap.ensureInitialized();

        assertThat(SandboxRegistry.getLauncher("pre_deploy")).isNotNull();
        assertThat(SandboxRegistry.getProviderClass("aio", "fs")).isNull();
    }

    @Test
    void createLauncherUnknownTypeShouldRaise() {
        assertThatThrownBy(() -> SandboxRegistry.createLauncher("_missing_launcher"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown launcher_type");
    }

    @Test
    void createProviderUnknownTypeShouldRaise() {
        assertThatThrownBy(() -> SandboxRegistry.createProvider("_missing_sandbox", "fs",
                SandboxEndpoint.builder().baseUrl("http://localhost:9000").sandboxId("sbx-1").build(), null))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("does not support operation");
    }
}
