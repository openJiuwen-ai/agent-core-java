/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

/**
 * Client wrapper for sandbox gateway full-chain routing and endpoint resolution.
 * <p>
 * Mirrors Python's {@code SandboxGatewayClient} in
 * {@code openjiuwen/core/sys_operation/sandbox/gateway/gateway_client.py}.
 * </p>
 */
public class SandboxGatewayClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxGatewayClient.class);

    private final SandboxGatewayConfig config;
    private final String isolationKey;
    private final SandboxGateway gateway;

    private volatile String cachedSandboxId;
    private volatile CompletableFuture<SandboxEndpoint> endpointFuture;

    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey, SandboxGateway gateway) {
        this.config = config;
        this.isolationKey = isolationKey;
        this.gateway = gateway == null ? SandboxGateway.getInstance(null) : gateway;
    }

    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey) {
        this(config, isolationKey, null);
    }

    public CompletableFuture<Object> invoke(String opType, String method, Map<String, Object> params) {
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType(opType)
                .method(method)
                .params(params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params))
                .isolationKey(isolationKey)
                .build();
        return gateway.handleRequest(config, request).thenApply(response -> {
            raiseIfFailed(response);
            return response.data();
        });
    }

    public CompletableFuture<Flow.Publisher<?>> invokeStream(String opType, String method, Map<String, Object> params) {
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType(opType)
                .method(method)
                .params(params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params))
                .isolationKey(isolationKey)
                .build();
        return gateway.handleStreamRequest(config, request);
    }

    public CompletableFuture<SandboxEndpoint> getEndpoint() {
        if (endpointFuture == null) {
            synchronized (this) {
                if (endpointFuture == null) {
                    SandboxCreateRequest request = SandboxCreateRequest.builder()
                            .isolationKey(isolationKey)
                            .config(config)
                            .build();
                    endpointFuture = gateway.getSandbox(request).thenApply(response -> {
                        raiseIfFailed(response);
                        Object endpoint = response.data();
                        if (endpoint instanceof SandboxEndpoint sandboxEndpoint) {
                            return sandboxEndpoint;
                        }
                        if (endpoint instanceof Map<?, ?> rawMap) {
                            String baseUrl = readMapString(rawMap, "baseUrl", "base_url");
                            String sandboxId = readMapString(rawMap, "sandboxId", "sandbox_id");
                            return new SandboxEndpoint(baseUrl, sandboxId);
                        }
                        throw new IllegalArgumentException(
                            "Invalid endpoint payload: " + endpoint.getClass().getSimpleName());
                    });
                }
            }
        }
        return endpointFuture;
    }

    /**
     * Returns the sandbox id for the current isolation key, with non-blocking first-call
     * behavior and degraded fallback during rebuild failures.
     *
     * <p>Critical fixes:
     * <ul>
     *   <li>C-1: isDone() pre-check avoids blocking first span creation (was 5-10s)
     *   <li>C-2: Cached old id provides fallback during rebuild failures (was permanent null)
     * </ul>
     *
     * @return the current sandbox id, or null if sandbox not yet initialized
     * @since 0.1.14
     */
    public String getSandboxId() {
        try {
            CompletableFuture<SandboxEndpoint> future = getEndpoint();

            // C-1 fix: avoid blocking span creation (first call returns null in 0ms)
            if (!future.isDone()) {
                return cachedSandboxId;
            }

            SandboxEndpoint endpoint = future.join();
            String currentId = endpoint.sandboxId();

            cachedSandboxId = currentId;
            return currentId;
        } catch (CompletionException | CancellationException exception) {
            // C-2 fix: return cached old sandboxId as degradation fallback
            LOGGER.warn("Failed to query sandbox id, using cached fallback: {}",
                cachedSandboxId, exception);
            return cachedSandboxId;
        }
    }

    public static CompletableFuture<Void> release(String isolationKey) {
        return release(isolationKey, "delete");
    }

    public static CompletableFuture<Void> release(String isolationKey, String onStop) {
        SandboxGateway gateway = SandboxGateway.getInstance(null);
        return gateway.releaseSandbox(isolationKey, onStop == null ? "delete" : onStop)
                .thenApply(response -> {
                    raiseIfFailed(response);
                    return null;
                });
    }

    static void raiseIfFailed(GatewayResponse response) {
        if (response == null) {
            throw ErrorHelper.buildError(
                    StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR,
                    "operation",
                    "gateway_unknown",
                    "error_msg",
                    "null response"
            );
        }
        Boolean legacySuccess = readResponseBoolean(response, "success");
        boolean success = legacySuccess != null
                ? legacySuccess
                : response.code() == StatusCode.SUCCESS.getCode();
        if (success) {
            return;
        }
        String opType = readResponseString(response, "opType", "op_type");
        String errorMessage = readResponseString(response, "error", "message");
        throw ErrorHelper.buildError(
                StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR,
                "operation",
                "gateway_" + (opType == null ? "unknown" : opType),
                "error_msg",
                errorMessage == null ? "unknown error" : errorMessage
        );
    }

    private static String readMapString(Map<?, ?> values, String primary, String fallback) {
        Object value = values.get(primary);
        if (value == null) {
            value = values.get(fallback);
        }
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean readResponseBoolean(GatewayResponse response, String propertyName) {
        try {
            Object value = response.getClass().getMethod(propertyName).invoke(response);
            return value instanceof Boolean bool ? bool : null;
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static String readResponseString(GatewayResponse response, String primary, String fallback) {
        try {
            Object value = response.getClass().getMethod(primary).invoke(response);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to the fallback property.
        }
        try {
            Object value = response.getClass().getMethod(fallback).invoke(response);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
