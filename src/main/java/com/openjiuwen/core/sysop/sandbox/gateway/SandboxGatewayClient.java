/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import java.util.Iterator;
import java.util.Map;

/**
 * Client wrapper for sandbox gateway — supports both endpoint resolution and full-chain invoke.
 * <p>
 * Provides convenient methods for invoking sandbox operations and managing endpoints.
 * <p>
 * Mirrors Python's {@code SandboxGatewayClient} in {@code sandbox/gateway/gateway_client.py}.
 */
public class SandboxGatewayClient {

    private final SandboxGatewayConfig config;
    private final String isolationKey;
    private final SandboxGateway gateway;

    /**
     * Create a SandboxGatewayClient.
     *
     * @param config        sandbox gateway configuration
     * @param isolationKey  optional isolation key
     * @param gateway       optional gateway instance (uses singleton if null)
     */
    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey, SandboxGateway gateway) {
        this.config = config;
        this.isolationKey = isolationKey;
        this.gateway = gateway != null ? gateway : SandboxGateway.getInstance();
    }

    /**
     * Create a SandboxGatewayClient with default gateway.
     *
     * @param config       sandbox gateway configuration
     * @param isolationKey optional isolation key
     */
    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey) {
        this(config, isolationKey, null);
    }

    // ── Full-chain routing API ──

    /**
     * Send an invoke request through the gateway full-chain routing.
     *
     * @param opType  operation type (fs/shell/code)
     * @param method  method name to invoke
     * @param params  method parameters
     * @return the invocation result
     * @throws RuntimeException if the request fails
     */
    public Object invoke(String opType, String method, Map<String, Object> params) {
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType(opType)
                .method(method)
                .params(params != null ? params : Map.of())
                .isolationKey(this.isolationKey)
                .build();

        GatewayResponse response = gateway.handleRequest(config, request);
        raiseIfFailed(response);
        return response.getData();
    }

    /**
     * Send a streaming invoke request through the gateway full-chain routing.
     *
     * @param opType  operation type (fs/shell/code)
     * @param method  method name to invoke
     * @param params  method parameters
     * @return an iterator for streaming results
     * @throws Exception if the request fails
     */
    public Iterator<?> invokeStream(String opType, String method, Map<String, Object> params) throws Exception {
        GatewayInvokeRequest request = GatewayInvokeRequest.builder()
                .opType(opType)
                .method(method)
                .params(params != null ? params : Map.of())
                .isolationKey(this.isolationKey)
                .build();

        return gateway.handleStreamRequest(config, request);
    }

    // ── Legacy endpoint-only API (kept for backward compatibility) ──

    /**
     * Get the sandbox endpoint for this client's isolation key.
     *
     * @return the SandboxEndpoint
     * @throws RuntimeException if endpoint resolution fails
     */
    public SandboxEndpoint getEndpoint() {
        SandboxCreateRequest request = SandboxCreateRequest.builder()
                .isolationKey(this.isolationKey)
                .config(this.config)
                .build();

        GatewayResponse response = gateway.getSandbox(request);
        raiseIfFailed(response);

        Object data = response.getData();
        if (data instanceof SandboxEndpoint) {
            return (SandboxEndpoint) data;
        }
        if (data instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) data;
            return SandboxEndpoint.builder()
                    .baseUrl((String) map.get("baseUrl"))
                    .sandboxId((String) map.get("sandboxId"))
                    .build();
        }
        throw new TypeErrorException("Invalid endpoint payload: " + data.getClass().getName());
    }

    /**
     * Static release method: Notify the gateway to reclaim resources.
     *
     * @param isolationKey the isolation key to release
     * @param onStop       behavior on stop: "delete", "pause", or "keep"
     * @throws RuntimeException if release fails
     */
    public static void release(String isolationKey, String onStop) {
        SandboxGateway gw = SandboxGateway.getInstance();
        GatewayResponse response = gw.releaseSandbox(isolationKey, onStop != null ? onStop : "delete");
        raiseIfFailed(response);
    }

    /**
     * Static release method with default delete behavior.
     *
     * @param isolationKey the isolation key to release
     * @throws RuntimeException if release fails
     */
    public static void release(String isolationKey) {
        release(isolationKey, "delete");
    }

    /**
     * Raise an error if the response indicates failure.
     *
     * @param response the gateway response to check
     * @throws RuntimeException if the response indicates failure
     */
    private static void raiseIfFailed(GatewayResponse response) {
        if (response.isSuccess()) {
            return;
        }

        String errorMsg = response.getMessage() != null ? response.getMessage() : "unknown error";
        throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR,
                "operation", "gateway_unknown",
                "error_msg", errorMsg);
    }

    /**
     * Simple exception for type errors (mirrors Python's TypeError).
     */
    private static class TypeErrorException extends RuntimeException {
        TypeErrorException(String message) {
            super(message);
        }
    }
}