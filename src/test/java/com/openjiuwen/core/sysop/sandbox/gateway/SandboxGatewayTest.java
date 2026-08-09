/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code SandboxGateway} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 */
class SandboxGatewayTest {

    @AfterEach
    void cleanupRegistry() {
        SandboxRegistry.unregisterLauncher("gateway-test-launcher");
        SandboxRegistry.unregisterProvider("gateway-test-sandbox", "shell");
    }

    @Test
    void handleRequestCreatesAndCachesProvider() {
        AtomicInteger providerConstructCount = new AtomicInteger();
        TestGatewayProvider.providerConstructCount = providerConstructCount;
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-test-sandbox", "shell", TestGatewayProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig();
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType("shell")
                .method("echo")
                .params(Map.of("text", "hello"))
                .isolationKey("iso-1")
                .build();

        GatewayResponse first = gateway.handleRequest(config, request).join();
        GatewayResponse second = gateway.handleRequest(config, request).join();

        assertEquals(StatusCode.SUCCESS.getCode(), first.code());
        assertEquals("echo:hello@http://sandbox", first.data());
        assertEquals("echo:hello@http://sandbox", second.data());
        assertEquals(1, providerConstructCount.get());
    }

    @Test
    void handleStreamRequestReturnsProviderPublisher() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-test-sandbox", "shell", TestGatewayProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig();
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType("shell")
                .method("stream")
                .params(Map.of("text", "hello"))
                .isolationKey("iso-2")
                .build();

        Flow.Publisher<?> publisher = gateway.handleStreamRequest(config, request).join();

        assertSame(TestGatewayProvider.lastPublisher, publisher);
    }

    @Test
    void releaseSandboxDeletesRecordAndCache() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-test-sandbox", "shell", TestGatewayProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig();

        GatewayResponse created = gateway.getSandbox(SandboxCreateRequest.builder()
                .config(config)
                .isolationKey("iso-3")
                .build()).join();
        GatewayResponse released = gateway.releaseSandbox("iso-3", "delete").join();
        GatewayResponse missing = gateway.releaseSandbox("iso-3", "delete").join();

        assertEquals(StatusCode.SUCCESS.getCode(), created.code());
        assertEquals(StatusCode.SUCCESS.getCode(), released.code());
        assertEquals(Boolean.TRUE, released.data());
        assertEquals(StatusCode.ERROR.getCode(), missing.code());
        assertEquals(Boolean.FALSE, missing.data());
        assertTrue(TestLauncher.deleteCount.get() >= 1);
    }

    private static SandboxGatewayConfig gatewayConfig() {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("gateway-test-launcher")
                .sandboxType("gateway-test-sandbox")
                .build();
        return SandboxGatewayConfig.builder()
                .launcherConfig(launcherConfig)
                .timeoutSeconds(30)
                .build();
    }

    public static final class TestLauncher extends SandboxLauncher {
        private static final AtomicInteger deleteCount = new AtomicInteger();

        @Override
        public CompletableFuture<LaunchedSandbox> launch(
                SandboxLauncherConfig config,
                int timeoutSeconds,
                String isolationKey) {
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox", "sb-" + isolationKey));
        }

        @Override
        public CompletableFuture<Void> delete(String sandboxId) {
            deleteCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    public static final class TestGatewayProvider {
        private static AtomicInteger providerConstructCount = new AtomicInteger();
        private static SubmissionPublisher<String> lastPublisher;
        private final SandboxEndpoint endpoint;

        public TestGatewayProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            this.endpoint = endpoint;
            providerConstructCount.incrementAndGet();
        }

        public CompletableFuture<String> echo(String text) {
            return CompletableFuture.completedFuture("echo:" + text + "@" + endpoint.baseUrl());
        }

        public Flow.Publisher<String> stream(String text) {
            lastPublisher = new SubmissionPublisher<>();
            lastPublisher.submit(text);
            lastPublisher.close();
            return lastPublisher;
        }
    }
}
