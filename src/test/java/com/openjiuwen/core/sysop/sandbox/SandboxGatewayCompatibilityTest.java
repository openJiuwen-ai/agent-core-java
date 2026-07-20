
package com.openjiuwen.core.sysop.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.testsupport.OsTestSupport;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class SandboxGatewayCompatibilityTest {
    @TempDir
    Path tempDir;

    private SandboxGatewayConfig config() {
        return SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                        .baseUrl("http://local-provider:9999").sandboxType("local").build())
                .params(Map.of("root_path", tempDir.toString(), "shell_allowlist",
                        List.of("pwd", "cd", "python3", "python", "echo")))
                .build();
    }

    @Test
    void sandboxClientShouldExposeOperations() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxClient client = new SandboxClient(config());

        assertThat(client.fs()).isNotNull();
        assertThat(client.shell()).isNotNull();
        assertThat(client.code()).isNotNull();
        assertThat(client.shell().executeCmd(OsTestSupport.cwdCommand(), ".", 300, null, null).getCode())
                .isEqualTo(StatusCode.SUCCESS.getCode());
    }

    @Test
    void containerManagerShouldCacheByKey() {
        SandboxTestLocalProviders.ensureRegistered();
        ContainerManager manager = new ContainerManager();
        SandboxClient first = manager.acquire("demo", config());
        SandboxClient second = manager.acquire("demo", config());

        assertThat(first).isSameAs(second);
        assertThat(manager.size()).isEqualTo(1);
        assertThat(manager.getContainer("demo")).isNotNull();
        assertThat(manager.getContainer("demo").getSandboxId()).isEqualTo("demo");
        assertThat(manager.release("demo")).isTrue();
    }

    @Test
    void gatewayShouldProvideSingletonAccess() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxGateway gateway = SandboxGateway.getInstance();
        SandboxClient first = gateway.connect("gateway-demo", config());
        SandboxClient second = gateway.connect("gateway-demo", config());

        assertThat(first).isSameAs(second);
        assertThat(gateway.containerManager().keys()).contains("gateway-demo");
        assertThat(gateway.disconnect("gateway-demo")).isTrue();
    }

    @Test
    void gatewayShouldHandleInvokeAndEndpointRequests() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxGateway gateway = SandboxGateway.createForTest();

        GatewayResponse response = gateway.handleRequest(config(),
                com.openjiuwen.core.sysop.config.GatewayInvokeRequest.builder().opType("shell").method("executeCmd")
                        .params(Map.of("command", OsTestSupport.cwdCommand(), "cwd", ".", "timeout", 300))
                        .isolationKey("invoke-demo").build());

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNotNull();

        GatewayResponse endpoint = gateway.getSandbox(com.openjiuwen.core.sysop.config.SandboxCreateRequest.builder()
                .isolationKey("invoke-demo").config(config()).build());
        assertThat(endpoint.isSuccess()).isTrue();
        assertThat(((SandboxEndpoint) endpoint.getData()).getSandboxId()).isEqualTo("invoke-demo");
    }

    @Test
    void gatewayClientShouldInvokeAndResolveEndpoint() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxGateway gateway = SandboxGateway.getInstance();
        SandboxGatewayClient client = new SandboxGatewayClient(config(), "client-demo", gateway);

        Object result = client.invoke("shell", "executeCmd",
                Map.of("command", OsTestSupport.cwdCommand(), "cwd", ".", "timeout", 300));
        SandboxEndpoint endpoint = client.getEndpoint();

        assertThat(result).isNotNull();
        assertThat(endpoint.getSandboxId()).isEqualTo("client-demo");
        SandboxGatewayClient.release("client-demo");
    }

    @Test
    void gatewayShouldFailWithoutRegisteredProvider() {
        SandboxGateway gateway = SandboxGateway.createForTest();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder().launcherConfig(SandboxLauncherConfig.builder()
                .launcherType("pre_deploy").baseUrl("http://localhost:8080").sandboxType("aio").build()).build();

        GatewayResponse response = gateway.handleRequest(config,
                com.openjiuwen.core.sysop.config.GatewayInvokeRequest.builder().opType("shell").method("executeCmd")
                        .params(Map.of("command", OsTestSupport.cwdCommand(), "cwd", ".", "timeout", 300))
                        .isolationKey("invoke-demo").build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).satisfiesAnyOf(
                msg -> assertThat(msg).contains("does not support operation"),
                msg -> assertThat(msg).contains("is not implemented"));
    }
}
