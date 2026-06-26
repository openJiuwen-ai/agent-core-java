/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGatewayClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Mixin providing gateway client management and invoke/invokeStream for sandbox operations.
 *
 * <p>Mirrors Python's {@code SandboxGatewayClientMixin} in
 * {@code openjiuwen/core/sys_operation/sandbox/sandbox_mixin.py}.</p>
 */
public class SandboxGatewayClientMixin {

    private static final String TEMPLATE_SESSION_PLACEHOLDER = "{session_id}";

    protected SandboxGatewayConfig config;
    protected String isolationKeyTemplate;
    protected String opType;
    private SandboxGatewayClient gatewayClient;

    /**
     * Resolve the isolation key template by replacing {@code {session_id}} with the current logging session id.
     */
    public static String resolveIsolationKeyTemplate(String template) {
        if (template == null) {
            return null;
        }
        if (template.contains(TEMPLATE_SESSION_PLACEHOLDER)) {
            String sessionId = LoggingUtils.getSessionId();
            String resolvedSessionId = (sessionId == null || sessionId.isBlank()) ? "default_session" : sessionId;
            return template.replace(TEMPLATE_SESSION_PLACEHOLDER, resolvedSessionId);
        }
        return template;
    }

    /**
     * Initialize the client context with runtime configuration.
     */
    protected void initClientContext(SandboxRunConfig runConfig, String opType) {
        this.config = runConfig.getConfig();
        this.isolationKeyTemplate = runConfig.getIsolationKeyTemplate();
        this.opType = opType;
    }

    /**
     * Resolve the isolation key template with the current session id from logging context.
     */
    protected String getResolvedIsolationKey() {
        return resolveIsolationKeyTemplate(isolationKeyTemplate);
    }

    /**
     * Invoke a provider method through the gateway full-chain routing.
     */
    public CompletableFuture<Object> invoke(String method, Map<String, Object> params) {
        return getGatewayClient().invoke(opType, method, params);
    }

    /**
     * Invoke a streaming provider method through the gateway full-chain routing.
     */
    public CompletableFuture<Flow.Publisher<?>> invokeStream(String method, Map<String, Object> params) {
        return getGatewayClient().invokeStream(opType, method, params);
    }

    protected SandboxGatewayClient createGatewayClient(String isolationKey) {
        return new SandboxGatewayClient(config, isolationKey);
    }

    protected synchronized SandboxGatewayClient getGatewayClient() {
        if (gatewayClient == null) {
            gatewayClient = createGatewayClient(getResolvedIsolationKey());
        }
        return gatewayClient;
    }
}
