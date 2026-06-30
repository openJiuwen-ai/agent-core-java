package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SandboxGatewayClientCompatibilityTest {

    @TempDir
    Path tempDir;

    private SandboxGatewayConfig config() {
        return SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://local-provider:9999")
                        .sandboxType("local")
                        .build())
                .params(Map.of(
                        "root_path", tempDir.toString(),
                        "shell_allowlist", List.of("pwd", "python3", "python", "echo")
                ))
                .build();
    }

    @Test
    void gatewayClientShouldInvokeAcrossFsShellAndCodeProviders() throws Exception {
        SandboxTestLocalProviders.ensureRegistered();
        Files.writeString(tempDir.resolve("hello.txt"), "hi");
        SandboxGatewayClient client = new SandboxGatewayClient(config(), "client-1");

        Object read = client.invoke("fs", "readFile", SandboxOperationSupport.params(new Object[] {
                "path", "hello.txt",
                "mode", "text",
                "head", null,
                "tail", null,
                "lineRange", null,
                "encoding", "utf-8",
                "chunkSize", 0,
                "options", null
        }));
        Object pwd = client.invoke("shell", "executeCmd", SandboxOperationSupport.params(new Object[] {
                "command", "pwd",
                "cwd", ".",
                "timeout", 300,
                "environment", null,
                "options", null
        }));
        Object code = client.invoke("code", "executeCode", SandboxOperationSupport.params(new Object[] {
                "code", "import os\nprint(os.getcwd())",
                "language", "python",
                "timeout", 300,
                "environment", null,
                "options", null
        }));

        assertThat(read).isInstanceOf(com.openjiuwen.core.sysop.result.ReadFileResult.class);
        assertThat(((com.openjiuwen.core.sysop.result.ReadFileResult) read).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(pwd).isInstanceOf(com.openjiuwen.core.sysop.result.ExecuteCmdResult.class);
        assertThat(((com.openjiuwen.core.sysop.result.ExecuteCmdResult) pwd).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(code).isInstanceOf(com.openjiuwen.core.sysop.result.ExecuteCodeResult.class);
        assertThat(((com.openjiuwen.core.sysop.result.ExecuteCodeResult) code).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
    }

    @Test
    void gatewayClientShouldResolveEndpointAndRelease() {
        SandboxTestLocalProviders.ensureRegistered();
        SandboxGatewayClient client = new SandboxGatewayClient(config(), "client-2");

        SandboxEndpoint endpoint = client.getEndpoint();
        SandboxGatewayClient.release("client-2", "keep");

        assertThat(endpoint.getSandboxId()).isEqualTo("client-2");
        assertThat(endpoint.getBaseUrl()).isNotNull();
    }
}
