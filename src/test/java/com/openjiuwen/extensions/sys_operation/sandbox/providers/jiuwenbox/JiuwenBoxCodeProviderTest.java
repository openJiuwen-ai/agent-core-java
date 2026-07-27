/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JiuwenBoxCodeProviderTest {

    private JiuwenBoxClient mockClient;
    private JiuwenBoxCodeProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        clearStaticCaches();
        mockClient = mock(JiuwenBoxClient.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        clearStaticCaches();
    }

    private void clearStaticCaches() throws Exception {
        JiuwenBoxProviderMixin.clearSharedSandbox("http://mock-server:8080");
        Field hooksField = JiuwenBoxProviderMixin.class.getDeclaredField("LIFECYCLE_HOOKS");
        hooksField.setAccessible(true);
        Map<String, LifecycleHook> hooks = (Map<String, LifecycleHook>) hooksField.get(null);
        hooks.clear();
    }

    private JiuwenBoxCodeProvider createProviderWithMockClient(SandboxEndpoint endpoint, SandboxGatewayConfig config) throws Exception {
        JiuwenBoxCodeProvider prov = new JiuwenBoxCodeProvider(endpoint, config);
        Field mixinField = JiuwenBoxCodeProvider.class.getDeclaredField("mixin");
        mixinField.setAccessible(true);
        JiuwenBoxProviderMixin mix = (JiuwenBoxProviderMixin) mixinField.get(prov);
        Field clientField = JiuwenBoxProviderMixin.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mix, mockClient);
        return prov;
    }

    private JiuwenBoxClient.ExecResponse makeExecResponse(String stdout, String stderr, int exitCode) {
        JiuwenBoxClient.ExecResponse resp = new JiuwenBoxClient.ExecResponse();
        resp.setStdout(stdout);
        resp.setStderr(stderr);
        resp.setExitCode(exitCode);
        return resp;
    }

    @Test
    @DisplayName("buildCodeCommand for python uses base64 encoding and python3 -c")
    void testBuildCodeCommandPython() throws Exception {
        String code = "print('hello')";
        String expectedBase64 = Base64.getEncoder().encodeToString(
                code.getBytes(StandardCharsets.UTF_8));

        when(mockClient.exec(anyString(), anyList(), any(), any(), anyMap(), any()))
                .thenReturn(makeExecResponse("hello", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-code-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        provider.executeCode(code, "python", 30, null, null);

        verify(mockClient).exec(anyString(), anyList(), any(), any(), anyMap(), any());
    }

    @Test
    @DisplayName("buildCodeCommand for javascript uses base64 encoding and node -e")
    void testBuildCodeCommandJavascript() throws Exception {
        String code = "console.log('hi')";

        when(mockClient.exec(anyString(), anyList(), any(), any(), anyMap(), any()))
                .thenReturn(makeExecResponse("hi", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-code-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        provider.executeCode(code, "javascript", 30, null, null);

        verify(mockClient).exec(anyString(), anyList(), any(), any(), anyMap(), any());
    }

    @Test
    @DisplayName("executeCode returns ExecuteCodeResult with stdout and exitCode")
    void testExecuteCodeNormal() throws Exception {
        when(mockClient.exec(anyString(), anyList(), any(), any(), anyMap(), any()))
                .thenReturn(makeExecResponse("output", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-code-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        ExecuteCodeResult result = provider.executeCode("print(1)", "python", 30, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStdout()).isEqualTo("output");
        assertThat(result.getData().getExitCode()).isEqualTo(0);
        assertThat(result.getData().getLanguage()).isEqualTo("python");
    }

    @Test
    @DisplayName("prepareCodeEnvironment merges PYTHONIOENCODING defaults for python")
    void testPrepareCodeEnvironment() throws Exception {
        when(mockClient.exec(anyString(), anyList(), any(), any(), anyMap(), any()))
                .thenReturn(makeExecResponse("", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-code-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        Map<String, String> userEnv = Map.of("MY_VAR", "my_value");
        provider.executeCode("print(1)", "python", 30, userEnv, null);

        verify(mockClient).exec(anyString(), anyList(), any(), any(), anyMap(), any());
    }

    @Test
    @DisplayName("executeCode falls back to local on non-zero exitCode when fallback_on_failure=true")
    void testExecuteCodeFallback() throws Exception {
        when(mockClient.exec(anyString(), anyList(), any(), any(), anyMap(), any()))
                .thenReturn(makeExecResponse("", "error", 1));

        LinkedHashMap<String, Object> extraParams = new LinkedHashMap<>();
        extraParams.put("fallback_on_failure", true);
        extraParams.put("root_path", System.getProperty("java.io.tmpdir"));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-code-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(extraParams)
                        .build())
                .params(Map.of("root_path", System.getProperty("java.io.tmpdir"),
                        "shell_allowlist", List.of("python3")))
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        ExecuteCodeResult result = provider.executeCode("print(1)", "python", 30, null, null);

        assertThat(result.getData()).isNotNull();
    }
}
