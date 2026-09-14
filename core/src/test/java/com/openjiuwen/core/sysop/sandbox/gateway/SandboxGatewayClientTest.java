/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SandboxGatewayClientTest {

    @AfterEach
    void resetSingleton() throws Exception {
        Field field = SandboxGateway.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void invokeBuildsGatewayRequestAndReturnsPayload() {
        FakeGateway gateway = new FakeGateway();
        SandboxGatewayClient client = new SandboxGatewayClient(SandboxGatewayConfig.builder().build(), "iso-1", gateway);

        Object data = client.invoke("shell", "echo", Map.of("text", "hello")).join();

        assertEquals("ok", data);
        assertEquals("shell", gateway.lastInvokeRequest.getOpType());
        assertEquals("echo", gateway.lastInvokeRequest.getMethod());
        assertEquals("iso-1", gateway.lastInvokeRequest.getIsolationKey());
        assertEquals(Map.of("text", "hello"), gateway.lastInvokeRequest.getParams());
    }

    @Test
    void getEndpointAcceptsLegacyMapPayload() {
        FakeGateway gateway = new FakeGateway();
        gateway.sandboxResponse = CompletableFuture.completedFuture(new GatewayResponse(
                StatusCode.SUCCESS.getCode(),
                "ok",
                Map.of("base_url", "http://sandbox", "sandbox_id", "sb-1")
        ));
        SandboxGatewayClient client = new SandboxGatewayClient(SandboxGatewayConfig.builder().build(), "iso-2", gateway);

        SandboxEndpoint endpoint = client.getEndpoint().join();

        assertEquals("iso-2", gateway.lastCreateRequest.getIsolationKey());
        assertEquals("http://sandbox", endpoint.baseUrl());
        assertEquals("sb-1", endpoint.sandboxId());
    }

    @Test
    void invokeRaisesMappedGatewayErrorOnFailure() {
        FakeGateway gateway = new FakeGateway();
        gateway.handleResponse = CompletableFuture.completedFuture(new GatewayResponse(
                StatusCode.ERROR.getCode(),
                "boom",
                null
        ));
        SandboxGatewayClient client = new SandboxGatewayClient(SandboxGatewayConfig.builder().build(), "iso-3", gateway);

        CompletionException error = assertThrows(
                CompletionException.class,
                () -> client.invoke("fs", "read_file", Map.of()).join()
        );
        assertInstanceOf(BaseError.class, error.getCause());
        BaseError gatewayError = (BaseError) error.getCause();
        assertEquals(StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR, gatewayError.getStatus());
    }

    @Test
    void releaseUsesSingletonGatewayAndDefaultsToDelete() throws Exception {
        FakeGateway gateway = new FakeGateway();
        Field field = SandboxGateway.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, gateway);

        SandboxGatewayClient.release("iso-4").join();

        assertEquals("iso-4", gateway.lastReleaseIsolationKey);
        assertEquals("delete", gateway.lastReleaseMode);
    }

    @Test
    void invokeStreamDelegatesPublisher() {
        FakeGateway gateway = new FakeGateway();
        Flow.Publisher<?> publisher = client(gateway).invokeStream("code", "run", Map.of()).join();
        assertSame(gateway.publisher, publisher);
    }

    private static SandboxGatewayClient client(FakeGateway gateway) {
        return new SandboxGatewayClient(SandboxGatewayConfig.builder().build(), "iso-stream", gateway);
    }

    private static final class FakeGateway extends SandboxGateway {
        private GatewayInvokeRequest lastInvokeRequest;
        private SandboxCreateRequest lastCreateRequest;
        private String lastReleaseIsolationKey;
        private String lastReleaseMode;
        private CompletableFuture<GatewayResponse> handleResponse =
                CompletableFuture.completedFuture(new GatewayResponse(StatusCode.SUCCESS.getCode(), "ok", "ok"));
        private CompletableFuture<GatewayResponse> sandboxResponse =
                CompletableFuture.completedFuture(new GatewayResponse(
                        StatusCode.SUCCESS.getCode(),
                        "ok",
                        new SandboxEndpoint("http://sandbox", "sb")
                ));
        private final SubmissionPublisher<String> publisher = new SubmissionPublisher<>();

        @Override
        public CompletableFuture<GatewayResponse> handleRequest(
                SandboxGatewayConfig config,
                GatewayInvokeRequest request) {
            this.lastInvokeRequest = request;
            return handleResponse;
        }

        @Override
        public CompletableFuture<Flow.Publisher<?>> handleStreamRequest(
                SandboxGatewayConfig config,
                GatewayInvokeRequest request) {
            this.lastInvokeRequest = request;
            return CompletableFuture.completedFuture(publisher);
        }

        @Override
        public CompletableFuture<GatewayResponse> getSandbox(SandboxCreateRequest request) {
            this.lastCreateRequest = request;
            return sandboxResponse;
        }

        @Override
        public CompletableFuture<GatewayResponse> releaseSandbox(String isolationKey, String onStop) {
            this.lastReleaseIsolationKey = isolationKey;
            this.lastReleaseMode = onStop;
            return CompletableFuture.completedFuture(new GatewayResponse(StatusCode.SUCCESS.getCode(), "ok", true));
        }
    }
}
