/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JiuwenBoxFSProviderTest {
    private JiuwenBoxClient mockClient;
    private JiuwenBoxFSProvider provider;
    private JiuwenBoxProviderMixin mixin;

    @BeforeEach
    void setUp() throws Exception {
        clearStaticCaches();
        mockClient = mock(JiuwenBoxClient.class);

        SandboxEndpoint endpoint =
            SandboxEndpoint.builder().baseUrl("http://mock-server:8080").sandboxId("sb-fs-test").build();
        SandboxGatewayConfig config =
            SandboxGatewayConfig.builder()
                    .launcherConfig(SandboxLauncherConfig.builder().launcherType("pre_deploy")
                            .baseUrl("http://mock-server:8080").sandboxType("jiuwenbox")
                            .extraParams(new LinkedHashMap<>()).build())
                    .build();

        provider = new JiuwenBoxFSProvider(endpoint, config);

        Field mixinField = JiuwenBoxFSProvider.class.getDeclaredField("mixin");
        mixinField.setAccessible(true);
        mixin = (JiuwenBoxProviderMixin) mixinField.get(provider);

        Field clientField = JiuwenBoxProviderMixin.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mixin, mockClient);
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

    @Test
    @DisplayName("readFile in text mode returns UTF-8 string content")
    void testReadFileTextMode() {
        byte[] fileContent = "line1\nline2\nline3".getBytes(StandardCharsets.UTF_8);
        when(mockClient.downloadBytes(anyString(), anyString())).thenReturn(fileContent);

        ReadFileResult result = provider.readFile("/root/a.txt", "text", null, null, null, "utf-8", 0, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getContent()).isEqualTo("line1\nline2\nline3");
        assertThat(result.getData().getMode()).isEqualTo("text");
    }

    @Test
    @DisplayName("readFile with head returns first N lines")
    void testReadFileWithHead() {
        byte[] fileContent = "line1\nline2\nline3\nline4\nline5".getBytes(StandardCharsets.UTF_8);
        when(mockClient.downloadBytes(anyString(), anyString())).thenReturn(fileContent);

        ReadFileResult result = provider.readFile("/root/a.txt", "text", 2, null, null, "utf-8", 0, null);

        assertThat(result.getData().getContent()).isEqualTo("line1\nline2");
    }

    @Test
    @DisplayName("readFile with tail returns last N lines")
    void testReadFileWithTail() {
        byte[] fileContent = "line1\nline2\nline3\nline4\nline5".getBytes(StandardCharsets.UTF_8);
        when(mockClient.downloadBytes(anyString(), anyString())).thenReturn(fileContent);

        ReadFileResult result = provider.readFile("/root/a.txt", "text", null, 2, null, "utf-8", 0, null);

        assertThat(result.getData().getContent()).isEqualTo("line4\nline5");
    }

    @Test
    @DisplayName("writeFile calls uploadBytes for non-append mode")
    void testWriteFileUpload() throws Exception {
        doNothing().when(mockClient).uploadBytes(anyString(), anyString(), any());

        WriteFileResult result =
            provider.writeFile("/root/out.txt", "payload", "text", false, false, true, "644", "utf-8", null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getSize()).isGreaterThan(0);
        verify(mockClient).uploadBytes(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("writeFile calls appendBytes for append mode")
    void testWriteFileAppend() throws Exception {
        doNothing().when(mockClient).appendBytes(anyString(), anyString(), any());

        WriteFileResult result = provider.writeFile("/root/out.txt", "extra", "text", false, false, true, "644",
                "utf-8", Map.of("append", true));

        assertThat(result.getCode()).isEqualTo(0);
        verify(mockClient).appendBytes(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("listFiles returns sorted file items")
    void testListFiles() {
        Map<String, Object> item1 = Map.of("path", "/root/b.txt", "type", "file", "name", "b.txt");
        Map<String, Object> item2 = Map.of("path", "/root/a.txt", "type", "file", "name", "a.txt");
        when(mockClient.listFiles(anyString(), anyString(), anyBoolean(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>(List.of(item1, item2)));

        ListFilesResult result = provider.listFiles("/root", false, null, null, false, null, null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getListItems()).hasSize(2);
        assertThat(result.getData().getListItems().get(0).getPath()).isEqualTo("/root/a.txt");
        assertThat(result.getData().getListItems().get(1).getPath()).isEqualTo("/root/b.txt");
    }

    @Test
    @DisplayName("searchFiles returns sorted matching items")
    void testSearchFiles() {
        Map<String, Object> item1 = Map.of("path", "/root/b.py", "type", "file", "name", "b.py");
        Map<String, Object> item2 = Map.of("path", "/root/a.py", "type", "file", "name", "a.py");
        when(mockClient.searchFiles(anyString(), anyString(), anyString(), any()))
                .thenReturn(new ArrayList<>(List.of(item1, item2)));

        SearchFilesResult result = provider.searchFiles("/root", "*.py", null);

        assertThat(result.getCode()).isEqualTo(0);
        assertThat(result.getData().getMatchingFiles()).hasSize(2);
        assertThat(result.getData().getMatchingFiles().get(0).getPath()).isEqualTo("/root/a.py");
    }
}
