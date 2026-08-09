/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class JiuwenBoxProviderMixinTest {
    private JiuwenBoxClient mockClient;
    private SandboxEndpoint endpoint;
    private SandboxGatewayConfig config;

    @BeforeEach
    void setUp() throws Exception {
        clearStaticCaches();
        mockClient = mock(JiuwenBoxClient.class);
        endpoint = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .sandboxId("sb-test")
                .build();
        config = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
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
        Field timeoutCacheField = JiuwenBoxProviderMixin.class.getDeclaredField("IDLE_TIMEOUT_CACHE");
        timeoutCacheField.setAccessible(true);
        Map<String, int[]> timeoutCache = (Map<String, int[]>) timeoutCacheField.get(null);
        timeoutCache.clear();
    }

    private JiuwenBoxProviderMixin createMixinWithMockClient(SandboxEndpoint ep, SandboxGatewayConfig cfg) throws Exception {
        JiuwenBoxProviderMixin mixin = new JiuwenBoxProviderMixin(ep, cfg);
        Field clientField = JiuwenBoxProviderMixin.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mixin, mockClient);
        return mixin;
    }

    private void setSandboxIdViaReflection(JiuwenBoxProviderMixin mixin, String id) throws Exception {
        Field sandboxIdField = JiuwenBoxProviderMixin.class.getDeclaredField("sandboxId");
        sandboxIdField.setAccessible(true);
        sandboxIdField.set(mixin, id);
    }

    @Test
    @DisplayName("registerSharedSandboxId stores and retrieves sandbox ID")
    void testSharedSandboxIdsCache() {
        JiuwenBoxProviderMixin.registerSharedSandboxId("http://mock-server:8080", "sb-shared-1");

        List<String> urls = JiuwenBoxProviderMixin.cachedBaseUrls();
        assertThat(urls).contains("http://mock-server:8080");

        List<String> cleared = JiuwenBoxProviderMixin.clearSharedSandbox("http://mock-server:8080");
        assertThat(cleared).contains("sb-shared-1");
    }

    @Test
    @DisplayName("getClient uses injected _ojw_okhttp_client from gateway params")
    void testGetClientUsesInjectedOkHttpClient() {
        OkHttpClient injected = new OkHttpClient();
        SandboxGatewayConfig cfg = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .params(Map.of("_ojw_okhttp_client", injected))
                .build();
        JiuwenBoxProviderMixin mixin = new JiuwenBoxProviderMixin(endpoint, cfg);

        assertThat(mixin.getClient().httpClient()).isSameAs(injected);
    }

    @Test
    @DisplayName("getSandboxId returns value when sandboxId is set via env var path")
    void testGetSandboxIdFromEnvVar() throws Exception {
        SandboxEndpoint epNoId = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .build();
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(epNoId, config);
        setSandboxIdViaReflection(mixin, "env-sb-123");

        String id = mixin.getSandboxId();
        assertThat(id).isEqualTo("env-sb-123");
    }

    @Test
    @DisplayName("getSandboxId returns sandboxId from endpoint")
    void testGetSandboxIdFromEndpoint() throws Exception {
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(endpoint, config);

        String id = mixin.getSandboxId();
        assertThat(id).isEqualTo("sb-test");
    }

    @Test
    @DisplayName("getSandboxId creates new sandbox when no ID found elsewhere")
    void testGetSandboxIdCreatesNewWhenNotFound() throws Exception {
        when(mockClient.createSandbox(anyMap())).thenReturn("sb-new-1");

        SandboxEndpoint epNoId = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .build();
        SandboxGatewayConfig cfgNoId = SandboxGatewayConfig.builder()
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType("pre_deploy")
                        .baseUrl("http://mock-server:8080")
                        .sandboxType("jiuwenbox")
                        .extraParams(new LinkedHashMap<>())
                        .build())
                .build();
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(epNoId, cfgNoId);

        String id = mixin.getSandboxId();
        assertThat(id).isEqualTo("sb-new-1");
        verify(mockClient).createSandbox(anyMap());
    }

    @Test
    @DisplayName("executeWithSandboxRetry succeeds on first try")
    void testExecuteWithSandboxRetrySuccess() throws Exception {
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(endpoint, config);
        mixin.getSandboxId();

        String result = mixin.executeWithSandboxRetry(sid -> "result-" + sid);
        assertThat(result).isEqualTo("result-sb-test");
    }

    @Test
    @DisplayName("executeWithSandboxRetry throws SandboxRecreateExhaustedException when retries exhausted")
    void testExecuteWithSandboxRetryExhausted() throws Exception {
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(endpoint, config);

        assertThatThrownBy(() -> mixin.executeWithSandboxRetry(sid -> {
            throw new SandboxNotFoundException(sid, 404, "sandbox not found");
        })).isInstanceOf(SandboxRecreateExhaustedException.class);
    }

    @Test
    @DisplayName("configureServerIdleTimeout dedupes identical timeout values")
    void testConfigureServerIdleTimeoutDedupe() throws Exception {
        doNothing().when(mockClient).setIdleTimeout(any(), any());

        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("pre_deploy")
                .baseUrl("http://mock-server:8080")
                .sandboxType("jiuwenbox")
                .idleTtlSeconds(600)
                .extraParams(new LinkedHashMap<>(Map.of("idle_check_interval", 60)))
                .build();
        SandboxGatewayConfig cfgWithTimeout = SandboxGatewayConfig.builder()
                .launcherConfig(launcherConfig)
                .build();

        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(endpoint, cfgWithTimeout);

        mixin.configureServerIdleTimeout();
        verify(mockClient).setIdleTimeout(600, 60);

        mixin.configureServerIdleTimeout();
        verify(mockClient, org.mockito.Mockito.times(1)).setIdleTimeout(600, 60);
    }

    @Test
    @DisplayName("lifecycle hook is called on before_create event")
    void testLifecycleHookRegistration() throws Exception {
        LifecycleHook hook = mock(LifecycleHook.class);
        when(mockClient.createSandbox(anyMap())).thenReturn("sb-hook-1");

        LinkedHashMap<String, Object> extraParams = new LinkedHashMap<>();
        extraParams.put("lifecycle_hook", hook);
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("pre_deploy")
                .baseUrl("http://mock-server:8080")
                .sandboxType("jiuwenbox")
                .extraParams(extraParams)
                .build();
        SandboxGatewayConfig cfgWithHook = SandboxGatewayConfig.builder()
                .launcherConfig(launcherConfig)
                .build();

        SandboxEndpoint epNoId = SandboxEndpoint.builder()
                .baseUrl("http://mock-server:8080")
                .build();
        JiuwenBoxProviderMixin mixin = createMixinWithMockClient(epNoId, cfgWithHook);

        mixin.getSandboxId();
        verify(hook).onEvent(eq("before_create"), anyMap());
    }

    @Test
    @DisplayName("clearSharedSandbox removes IDs for matching baseUrl prefixes")
    void testClearSharedSandbox() {
        JiuwenBoxProviderMixin.registerSharedSandboxId("http://mock-server:8080|opt1", "sb-1");
        JiuwenBoxProviderMixin.registerSharedSandboxId("http://mock-server:8080|opt2", "sb-2");

        List<String> removed = JiuwenBoxProviderMixin.clearSharedSandbox("http://mock-server:8080");
        assertThat(removed).containsExactlyInAnyOrder("sb-1", "sb-2");

        List<String> urls = JiuwenBoxProviderMixin.cachedBaseUrls();
        assertThat(urls).doesNotContain("http://mock-server:8080");
    }
}
