/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.GatewayInvokeRequest;
import com.openjiuwen.core.sysop.config.SandboxCreateRequest;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;

import java.util.Map;

/**
 * Client wrapper for sandbox gateway full-chain routing.
 * 
 * @since 0.1.7
 */
public class SandboxGatewayClient {
    private final SandboxGatewayConfig config;
    private final String isolationKey;
    private final SandboxGateway gateway;

    /**
     * SandboxGatewayClient.
     * 
     * @param config config
     * @param isolationKey isolationKey
     * @since 0.1.7
     */
    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey) {
        this(config, isolationKey, SandboxGateway.getInstance());
    }

    /**
     * SandboxGatewayClient.
     * 
     * @param config config
     * @param isolationKey isolationKey
     * @param gateway gateway
     * @since 0.1.7
     */
    public SandboxGatewayClient(SandboxGatewayConfig config, String isolationKey, SandboxGateway gateway) {
        this.config = config != null ? config : SandboxGatewayConfig.builder().build();
        this.isolationKey = isolationKey;
        this.gateway = gateway != null ? gateway : SandboxGateway.getInstance();
    }

    /**
     * invoke.
     * 
     * @param opType opType
     * @param method method
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    public Object invoke(String opType, String method, Map<String, Object> params) {
        GatewayResponse response = gateway.handleRequest(config, GatewayInvokeRequest.builder().opType(opType)
                .method(method).params(params != null ? params : Map.of()).isolationKey(isolationKey).build());
        raiseIfFailed(response, opType);
        return response.getData();
    }

    /**
     * getEndpoint.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SandboxEndpoint getEndpoint() {
        GatewayResponse response =
            gateway.getSandbox(SandboxCreateRequest.builder().isolationKey(isolationKey).config(config).build());
        raiseIfFailed(response, "endpoint");
        return requireData(response, SandboxEndpoint.class);
    }

    /**
     * release.
     * 
     * @param isolationKey isolationKey
     * @since 0.1.7
     */
    public static void release(String isolationKey) {
        release(isolationKey, "delete");
    }

    /**
     * release.
     * 
     * @param isolationKey isolationKey
     * @param onStop onStop
     * @since 0.1.7
     */
    public static void release(String isolationKey, String onStop) {
        GatewayResponse response = SandboxGateway.getInstance().releaseSandbox(isolationKey, onStop);
        if (!response.isSuccess()) {
            throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, "process", "gateway_release",
                    "error_msg", response.getMessage());
        }
    }

    /**
     * raiseIfFailed.
     * 
     * @param response response
     * @param opType opType
     * @since 0.1.7
     */
    private static void raiseIfFailed(GatewayResponse response, String opType) {
        if (response != null && response.isSuccess()) {
            return;
        }
        throw ErrorHelper.buildError(StatusCode.SYS_OPERATION_MANAGER_PROCESS_ERROR, "process", "gateway_" + opType,
                "error_msg", response != null ? response.getMessage() : "unknown error");
    }

    static <T> T requireData(GatewayResponse response, Class<T> type) {
        Object data = response != null ? response.getData() : null;
        if (type.isInstance(data)) {
            return type.cast(data);
        }
        throw new IllegalArgumentException("Unexpected gateway response data type");
    }
}
