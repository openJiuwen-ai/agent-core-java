/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.extensions.sys_operation.sandbox.JiuwenBoxSandboxProfile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies the JiuwenBox gateway lifecycle without binding a local HTTP port.
 */
class JiuwenBoxGatewayLifecycleTest {
    private static final String BASE_URL = "http://jiuwenbox.mock:8321";

    private final InMemorySandboxStore store = new InMemorySandboxStore();
    private final SandboxGateway gateway = new SandboxGateway(null, store);
    private final Set<String> isolationKeys = new LinkedHashSet<>();

    @AfterEach
    void releaseTestSandboxes() {
        for (String isolationKey : isolationKeys) {
            if (store.get(isolationKey).isPresent()) {
                assertEquals(StatusCode.SUCCESS.getCode(),
                        gateway.releaseSandbox(isolationKey, "delete").join().code());
            }
        }
    }

    @Test
    void gatewayUsesPhysicalIdsWithinEachIsolationScope() throws IOException, InterruptedException {
        List<HttpRequest> requests = new ArrayList<>();
        AtomicInteger createCalls = new AtomicInteger();
        SandboxGatewayConfig config = gatewayConfig(mockHttpClient(requests, createCalls));

        gateway.handleRequest(config, shellRequest("session:jiuwenbox:first")).join();
        gateway.handleRequest(config, shellRequest("session:jiuwenbox:first")).join();
        gateway.handleRequest(config, codeRequest("session:jiuwenbox:first")).join();
        gateway.handleRequest(config, shellRequest("session:jiuwenbox:second")).join();
        GatewayResponse released = gateway.releaseSandbox("session:jiuwenbox:first", "delete").join();

        assertEquals(2, createCalls.get());
        assertEquals(
                List.of(
                        "/api/v1/sandboxes/physical-1/exec",
                        "/api/v1/sandboxes/physical-1/exec",
                        "/api/v1/sandboxes/physical-1/exec",
                        "/api/v1/sandboxes/physical-2/exec"),
                pathsForMethod(requests, "POST", "/exec"));
        assertEquals(List.of("/api/v1/sandboxes/physical-1"), pathsForMethod(requests, "DELETE", null));
        assertEquals(StatusCode.SUCCESS.getCode(), released.code());
        assertTrue(requests.stream().noneMatch(request -> request.uri().getPath().contains("session:jiuwenbox")));
    }

    @Test
    void idleEvictionDeletesTheResolvedPhysicalId() throws IOException, InterruptedException {
        List<HttpRequest> requests = new ArrayList<>();
        AtomicInteger createCalls = new AtomicInteger();
        SandboxGatewayConfig config = gatewayConfig(mockHttpClient(requests, createCalls));
        config.getLauncherConfig().setIdleTtlSeconds(-1);

        gateway.handleRequest(config, shellRequest("session:idle:first")).join();
        gateway.handleRequest(config, shellRequest("session:idle:second")).join();
        GatewayResponse released = gateway.releaseSandbox("session:idle:second", "delete").join();

        assertEquals(2, createCalls.get());
        assertEquals(
                List.of("/api/v1/sandboxes/physical-1", "/api/v1/sandboxes/physical-2"),
                pathsForMethod(requests, "DELETE", null));
        assertEquals(StatusCode.SUCCESS.getCode(), released.code());
    }

    @Test
    void configuredSandboxIdSkipsDynamicCreation() throws IOException, InterruptedException {
        List<HttpRequest> requests = new ArrayList<>();
        AtomicInteger createCalls = new AtomicInteger();
        SandboxGatewayConfig config = JiuwenBoxSandboxProfile.config(
                BASE_URL,
                Map.of("sandbox_id", "configured-physical-id"));
        config.getParams().put("_ojw_http_client", mockHttpClient(requests, createCalls));

        GatewayResponse response = gateway.handleRequest(
                config,
                shellRequest("session:configured")).join();

        assertEquals(StatusCode.SUCCESS.getCode(), response.code());
        assertEquals(0, createCalls.get());
        assertEquals(
                List.of("/api/v1/sandboxes/configured-physical-id/exec"),
                pathsForMethod(requests, "POST", "/exec"));
    }

    @Test
    void interruptedDeleteReportsFailureAndCanBeRetried() throws IOException, InterruptedException {
        List<HttpRequest> requests = new ArrayList<>();
        AtomicInteger createCalls = new AtomicInteger();
        SandboxGatewayConfig config = gatewayConfig(mockHttpClient(requests, createCalls, new AtomicBoolean(true)));
        assertEquals(StatusCode.SUCCESS.getCode(),
                gateway.handleRequest(config, shellRequest("session:interrupted")).join().code());

        GatewayResponse interrupted = gateway.releaseSandbox("session:interrupted", "delete").join();

        assertEquals(StatusCode.ERROR.getCode(), interrupted.code());
        assertEquals(Boolean.FALSE, interrupted.data());
        assertEquals("mock delete interrupted", interrupted.message());
        assertEquals("physical-1", store.get("session:interrupted").orElseThrow().getSandboxId());
        assertEquals(StatusCode.SUCCESS.getCode(),
                gateway.releaseSandbox("session:interrupted", "delete").join().code());
        assertEquals(1, createCalls.get());
        assertEquals(List.of("/api/v1/sandboxes/physical-1", "/api/v1/sandboxes/physical-1"),
                pathsForMethod(requests, "DELETE", null));
        assertTrue(store.get("session:interrupted").isEmpty());
    }

    private static SandboxGatewayConfig gatewayConfig(HttpClient httpClient) {
        SandboxGatewayConfig config = JiuwenBoxSandboxProfile.config(BASE_URL);
        config.getParams().put("_ojw_http_client", httpClient);
        return config;
    }

    private GatewayInvokeRequest shellRequest(String isolationKey) {
        isolationKeys.add(isolationKey);
        return GatewayInvokeRequest.builder()
                .opType("shell")
                .method("executeCmd")
                .params(Map.of("command", "echo sandbox-id"))
                .isolationKey(isolationKey)
                .build();
    }

    private GatewayInvokeRequest codeRequest(String isolationKey) {
        isolationKeys.add(isolationKey);
        return GatewayInvokeRequest.builder()
                .opType("code")
                .method("executeCode")
                .params(Map.of("code", "print('shared')", "language", "python"))
                .isolationKey(isolationKey)
                .build();
    }

    private static HttpClient mockHttpClient(List<HttpRequest> requests, AtomicInteger createCalls)
            throws IOException, InterruptedException {
        return mockHttpClient(requests, createCalls, new AtomicBoolean(false));
    }

    private static HttpClient mockHttpClient(List<HttpRequest> requests, AtomicInteger createCalls,
            AtomicBoolean shouldInterruptDelete) throws IOException, InterruptedException {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            requests.add(request);
            String path = request.uri().getPath();
            int statusCode = 200;
            String body = "";
            if ("POST".equals(request.method()) && "/api/v1/sandboxes".equals(path)) {
                int sandboxNumber = createCalls.incrementAndGet();
                statusCode = 201;
                body = "{\"id\":\"physical-" + sandboxNumber + "\"}";
            } else if ("POST".equals(request.method()) && path.endsWith("/exec")) {
                body = "{\"stdout\":\"ok\\n\",\"stderr\":\"\",\"exit_code\":0}";
            } else if ("DELETE".equals(request.method())) {
                if (shouldInterruptDelete.getAndSet(false)) {
                    throw new InterruptedException("mock delete interrupted");
                }
                statusCode = 204;
            } else if ("PUT".equals(request.method()) && "/api/v1/timeout".equals(path)) {
                body = "{}";
            } else {
                throw new AssertionError("Unexpected sandbox request: " + request.method() + " " + path);
            }
            return mockResponse(statusCode, body);
        });
        return httpClient;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    private static List<String> pathsForMethod(List<HttpRequest> requests, String method, String pathSuffix) {
        return requests.stream()
                .filter(request -> method.equals(request.method()))
                .map(request -> request.uri().getPath())
                .filter(path -> pathSuffix == null || path.endsWith(pathSuffix))
                .toList();
    }
}
