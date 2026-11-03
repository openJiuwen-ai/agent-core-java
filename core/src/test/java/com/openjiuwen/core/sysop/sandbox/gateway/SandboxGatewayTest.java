/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mirrors Python's {@code SandboxGateway} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway.py}.
 */
class SandboxGatewayTest {

    @AfterEach
    void cleanupRegistry() {
        TestLauncher.deleteResult = CompletableFuture.completedFuture(null);
        TestLauncher.pauseResult = CompletableFuture.completedFuture(null);
        TestLauncher.pausedSandboxIds.clear();
        TestDynamicProvider.deleteResult = CompletableFuture.completedFuture(null);
        SandboxRegistry.unregisterLauncher("gateway-test-launcher");
        SandboxRegistry.unregisterProvider("gateway-test-sandbox", "shell");
        SandboxRegistry.unregisterProvider("gateway-dynamic-sandbox", "shell");
    }

    @Test
    void releaseDetachesRecordAndDefersReuseUntilDeletionCompletes() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-test-sandbox", "shell", TestGatewayProvider.class);
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxGateway gateway = new SandboxGateway(null, store);
        Object oldProvider = gateway.getOrCreateProvider(gatewayConfig(), "pending", "shell").join();
        SandboxRecord oldRecord = store.get("pending").orElseThrow();

        completePendingDeletion(gateway, store);

        assertNotSame(oldProvider, gateway.getOrCreateProvider(gatewayConfig(), "pending", "shell").join());
        assertNotSame(oldRecord, store.get("pending").orElseThrow());
    }

    private static void completePendingDeletion(SandboxGateway gateway, InMemorySandboxStore store) {
        TestLauncher.deleteResult = new CompletableFuture<>();
        int initialDeleteCount = TestLauncher.deleteCount.get();

        CompletableFuture<GatewayResponse> release = gateway.releaseSandbox("pending", "delete");
        assertTrue(store.get("pending").isEmpty());
        assertEquals(Boolean.FALSE, gateway.releaseSandbox("pending", "delete").join().data());
        CompletableFuture<SandboxEndpoint> endpoint = gateway.getEndpoint(gatewayConfig(), "pending");
        CompletableFuture<Object> provider = gateway.getOrCreateProvider(gatewayConfig(), "pending", "shell");
        assertFalse(endpoint.isDone());
        assertFalse(provider.isDone());
        assertEquals(initialDeleteCount + 1, TestLauncher.deleteCount.get());

        TestLauncher.deleteResult.complete(null);

        assertEquals(Boolean.TRUE, release.join().data());
        assertEquals("sb-pending", endpoint.join().sandboxId());
        assertTrue(provider.isDone());
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "pause"})
    void releaseCompletionDoesNotRemoveReplacementRecord(String onStop) {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxGateway gateway = new SandboxGateway(null, store);
        gateway.getEndpoint(gatewayConfig(), "replace").join();
        TestLauncher.deleteResult = new CompletableFuture<>();
        TestLauncher.pauseResult = TestLauncher.deleteResult;
        CompletableFuture<GatewayResponse> release = gateway.releaseSandbox("replace", onStop);
        SandboxRecord replacement = new SandboxRecord("new-id", "http://sandbox", SandboxStatus.RUNNING,
                "gateway-test-launcher", "gateway-test-sandbox", "hash");
        store.set("replace", replacement);

        TestLauncher.deleteResult.complete(null);

        assertEquals(Boolean.TRUE, release.join().data());
        assertSame(replacement, store.get("replace").orElseThrow());
    }

    @Test
    void cancellingReleaseResponseDoesNotBlockLaterAcquisition() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxGateway gateway = new SandboxGateway();
        gateway.getEndpoint(gatewayConfig(), "cancelled-release").join();
        TestLauncher.deleteResult = new CompletableFuture<>();
        CompletableFuture<GatewayResponse> release = gateway.releaseSandbox("cancelled-release", "delete");
        CompletableFuture<SandboxEndpoint> endpoint = gateway.getEndpoint(gatewayConfig(), "cancelled-release");

        assertTrue(release.cancel(false));
        TestLauncher.deleteResult.complete(null);

        assertTrue(endpoint.isDone());
        assertEquals("sb-cancelled-release", endpoint.join().sandboxId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "pause"})
    void failedReleaseRestoresRecordAndAllowsRetry(String onStop) {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxGateway gateway = new SandboxGateway(null, store);
        gateway.getEndpoint(gatewayConfig(), "retry").join();
        SandboxRecord original = store.get("retry").orElseThrow();

        failPendingRelease(gateway, onStop);

        assertSame(original, store.get("retry").orElseThrow());
        TestLauncher.deleteResult = CompletableFuture.completedFuture(null);
        assertEquals(Boolean.TRUE, gateway.releaseSandbox("retry", "delete").join().data());
    }

    private static void failPendingRelease(SandboxGateway gateway, String onStop) {
        TestLauncher.deleteResult = new CompletableFuture<>();
        TestLauncher.pauseResult = TestLauncher.deleteResult;
        CompletableFuture<GatewayResponse> release = gateway.releaseSandbox("retry", onStop);
        CompletableFuture<SandboxEndpoint> endpoint = gateway.getEndpoint(gatewayConfig(), "retry");

        TestLauncher.deleteResult.completeExceptionally(new IllegalStateException("delete failed"));

        assertEquals(Boolean.FALSE, release.join().data());
        assertEquals("delete failed", release.join().message());
        assertEquals("sb-retry", endpoint.join().sandboxId());
    }

    @Test
    void acquisitionWaitsForPauseAndResumesSameSandbox() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxGateway gateway = new SandboxGateway();
        gateway.getEndpoint(gatewayConfig(), "pending-pause").join();
        TestLauncher.pauseResult = new CompletableFuture<>();
        CompletableFuture<GatewayResponse> release = gateway.releaseSandbox("pending-pause", "pause");
        CompletableFuture<SandboxEndpoint> endpoint = gateway.getEndpoint(gatewayConfig(), "pending-pause");
        assertFalse(endpoint.isDone());
        assertEquals(Boolean.FALSE, gateway.releaseSandbox("pending-pause", "pause").join().data());

        TestLauncher.pauseResult.complete(null);

        assertEquals(Boolean.TRUE, release.join().data());
        assertEquals("sb-pending-pause", endpoint.join().sandboxId());
        assertEquals("sb-pending-pause", TestLauncher.lastResumedSandboxId);
    }

    @Test
    void failedProviderReleasePreservesPhysicalDeletionForRetry() {
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-dynamic-sandbox", "shell", TestDynamicProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig("gateway-dynamic-sandbox");
        gateway.handleRequest(config, GatewayInvokeRequest.builder()
                .opType("shell").method("resolve").params(Map.of("text", "initialized"))
                .isolationKey("provider-retry").build()).join();
        Object provider = gateway.getOrCreateProvider(config, "provider-retry", "shell").join();
        TestDynamicProvider.deleteResult = CompletableFuture.failedFuture(new IllegalStateException("delete failed"));

        assertEquals(Boolean.FALSE, gateway.releaseSandbox("provider-retry", "delete").join().data());
        assertSame(provider, gateway.getOrCreateProvider(config, "provider-retry", "shell").join());
        TestDynamicProvider.deletedSandboxId = null;
        TestDynamicProvider.deleteResult = CompletableFuture.completedFuture(null);

        assertEquals(Boolean.TRUE, gateway.releaseSandbox("provider-retry", "delete").join().data());
        assertEquals("physical-sandbox-id", TestDynamicProvider.deletedSandboxId);
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

    @Test
    void providerResolvedSandboxIdIsPersistedAndUsedForRelease() {
        TestDynamicProvider.deletedSandboxId = null;
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-dynamic-sandbox", "shell", TestDynamicProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig("gateway-dynamic-sandbox");
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType("shell")
                .method("resolve")
                .params(Map.of("text", "initialized"))
                .isolationKey("iso-dynamic")
                .build();

        GatewayResponse invoked = gateway.handleRequest(config, request).join();
        GatewayResponse endpoint = gateway.getSandbox(SandboxCreateRequest.builder()
                .config(config)
                .isolationKey("iso-dynamic")
                .build()).join();
        GatewayResponse released = gateway.releaseSandbox("iso-dynamic", "delete").join();

        assertEquals(StatusCode.SUCCESS.getCode(), invoked.code());
        assertEquals("physical-sandbox-id", requireSandboxEndpoint(endpoint.data()).sandboxId());
        assertEquals(StatusCode.SUCCESS.getCode(), released.code());
        assertEquals("physical-sandbox-id", TestDynamicProvider.deletedSandboxId);
    }

    @Test
    void pauseAndResumeUseTheResolvedProviderSandboxId() {
        TestLauncher.pausedSandboxIds.clear();
        TestLauncher.lastPausedSandboxId = null;
        TestLauncher.lastResumedSandboxId = null;
        SandboxRegistry.registerLauncher("gateway-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("gateway-dynamic-sandbox", "shell", TestDynamicProvider.class);
        SandboxGateway gateway = new SandboxGateway();
        SandboxGatewayConfig config = gatewayConfig("gateway-dynamic-sandbox");
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType("shell")
                .method("resolve")
                .params(Map.of("text", "initialized"))
                .isolationKey("iso-pause")
                .build();

        gateway.handleRequest(config, request).join();
        GatewayResponse paused = gateway.releaseSandbox("iso-pause", "pause").join();
        GatewayResponse resumed = gateway.getSandbox(SandboxCreateRequest.builder()
                .config(config)
                .isolationKey("iso-pause")
                .build()).join();

        assertEquals(StatusCode.SUCCESS.getCode(), paused.code());
        assertEquals("physical-sandbox-id", TestLauncher.lastPausedSandboxId);
        assertEquals("physical-sandbox-id", TestLauncher.lastResumedSandboxId);
        assertEquals("physical-sandbox-id", requireSandboxEndpoint(resumed.data()).sandboxId());
    }

    private static SandboxEndpoint requireSandboxEndpoint(Object endpoint) {
        if (endpoint instanceof SandboxEndpoint sandboxEndpoint) {
            return sandboxEndpoint;
        }
        throw new AssertionError("Gateway response did not contain a sandbox endpoint");
    }

    private static SandboxGatewayConfig gatewayConfig() {
        return gatewayConfig("gateway-test-sandbox");
    }

    private static SandboxGatewayConfig gatewayConfig(String sandboxType) {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("gateway-test-launcher")
                .sandboxType(sandboxType)
                .build();
        return SandboxGatewayConfig.builder()
                .launcherConfig(launcherConfig)
                .timeoutSeconds(30)
                .build();
    }

    public static final class TestLauncher extends SandboxLauncher {
        private static final AtomicInteger deleteCount = new AtomicInteger();
        private static final Set<String> pausedSandboxIds = ConcurrentHashMap.newKeySet();
        private static volatile String lastPausedSandboxId;
        private static volatile String lastResumedSandboxId;
        private static CompletableFuture<Void> deleteResult = CompletableFuture.completedFuture(null);
        private static CompletableFuture<Void> pauseResult = CompletableFuture.completedFuture(null);

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
            return deleteResult;
        }

        @Override
        public CompletableFuture<Void> pause(String sandboxId) {
            lastPausedSandboxId = sandboxId;
            pausedSandboxIds.add(sandboxId);
            return pauseResult;
        }

        @Override
        public CompletableFuture<Void> resume(String sandboxId) {
            lastResumedSandboxId = sandboxId;
            pausedSandboxIds.remove(sandboxId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SandboxStatus> checkStatus(String sandboxId) {
            SandboxStatus status = pausedSandboxIds.contains(sandboxId)
                    ? SandboxStatus.PAUSED
                    : SandboxStatus.RUNNING;
            return CompletableFuture.completedFuture(status);
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

    /**
     * Test provider that resolves its sandbox ID during invocation.
     *
     * @since 0.1.17
     */
    public static final class TestDynamicProvider implements SandboxLifecycleProvider {
        private static volatile String deletedSandboxId;
        private static CompletableFuture<Void> deleteResult = CompletableFuture.completedFuture(null);

        private volatile String sandboxId;

        public TestDynamicProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        }

        /**
         * Resolves this test provider's sandbox and returns its input text.
         *
         * @param text text to return
         * @return the supplied text
         * @since 0.1.17
         */
        public CompletableFuture<String> resolve(String text) {
            sandboxId = "physical-sandbox-id";
            return CompletableFuture.completedFuture(text);
        }

        @Override
        public String getSandboxId() {
            return sandboxId;
        }

        @Override
        public String getSandboxBaseUrl() {
            return "http://sandbox";
        }

        @Override
        public CompletableFuture<Void> deleteSandbox(String physicalSandboxId) {
            deletedSandboxId = physicalSandboxId;
            return deleteResult;
        }
    }
}
