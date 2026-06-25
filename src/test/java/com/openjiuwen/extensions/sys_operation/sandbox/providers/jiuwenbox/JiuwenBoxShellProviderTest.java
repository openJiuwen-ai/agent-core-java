/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
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
import static org.mockito.Mockito.when;

class JiuwenBoxShellProviderTest {

    private JiuwenBoxClient mockClient;
    private JiuwenBoxShellProvider provider;

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

    private JiuwenBoxShellProvider createProviderWithMockClient(SandboxEndpoint endpoint, SandboxGatewayConfig config) throws Exception {
        JiuwenBoxShellProvider prov = new JiuwenBoxShellProvider(endpoint, config);
        Field mixinField = JiuwenBoxShellProvider.class.getDeclaredField("mixin");
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
    @DisplayName("executeCmd returns stdout/stderr/exitCode from sandbox exec")
    void testExecuteCmdNormal() throws Exception {
        when(mockClient.exec(anyString(), anyList(), anyString(), anyInt(), any(), any()))
                .thenReturn(makeExecResponse("hello world", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-shell-test")
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

        ExecuteCmdResult result = provider.executeCmd("echo hello", ".", 30, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getStdout()).isEqualTo("hello world");
        assertThat(result.getData().getExitCode()).isEqualTo(0);
        assertThat(result.getData().getShellType()).isEqualTo("bash");
    }

    @Test
    @DisplayName("executeCmd routes excluded command to local execution")
    void testExecuteCmdExcludedCommand() throws Exception {
        LinkedHashMap<String, Object> extraParams = new LinkedHashMap<>();
        extraParams.put("excluded_commands", List.of("pwd"));
        extraParams.put("root_path", System.getProperty("java.io.tmpdir"));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-shell-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(extraParams)
                        .build())
                .params(Map.of("root_path", System.getProperty("java.io.tmpdir"),
                        "shell_allowlist", List.of("pwd")))
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        ExecuteCmdResult result = provider.executeCmd("pwd", ".", 30, null, null);

        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("executeCmd falls back to local on non-zero exitCode when fallback_on_failure=true")
    void testExecuteCmdFallbackOnFailure() throws Exception {
        when(mockClient.exec(anyString(), anyList(), anyString(), anyInt(), any(), any()))
                .thenReturn(makeExecResponse("", "error", 1));

        LinkedHashMap<String, Object> extraParams = new LinkedHashMap<>();
        extraParams.put("fallback_on_failure", true);
        extraParams.put("root_path", System.getProperty("java.io.tmpdir"));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-shell-test")
                .build();
        SandboxGatewayConfig config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(extraParams)
                        .build())
                .params(Map.of("root_path", System.getProperty("java.io.tmpdir"),
                        "shell_allowlist", List.of("pwd")))
                .build();
        provider = createProviderWithMockClient(endpoint, config);

        ExecuteCmdResult result = provider.executeCmd("pwd", ".", 30, null, null);

        assertThat(result.getData()).isNotNull();
    }

    @Test
    @DisplayName("executeCmdStream splits stdout into per-line chunks")
    void testExecuteCmdStream() throws Exception {
        when(mockClient.exec(anyString(), anyList(), anyString(), anyInt(), any(), any()))
                .thenReturn(makeExecResponse("line1\nline2", "", 0));

        SandboxEndpoint endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-shell-test")
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

        Iterator<ExecuteCmdStreamResult> stream = provider.executeCmdStream("echo", ".", 30, null, null);

        assertThat(stream.hasNext()).isTrue();
        ExecuteCmdStreamResult first = stream.next();
        assertThat(first.getData().getText()).isEqualTo("line1");
        assertThat(stream.hasNext()).isTrue();
        ExecuteCmdStreamResult last = stream.next();
        assertThat(last.getData().getText()).isEqualTo("line2");
        assertThat(last.getData().getExitCode()).isEqualTo(0);
    }
}
