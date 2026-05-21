/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;

import java.util.Iterator;
import java.util.Map;

/**
 * Mixin providing gateway client management and invoke/invokeStream for sandbox operations.
 * <p>
 * Use this mixin in sandbox operation classes to enable gateway-based invocation.
 * <p>
 * Mirrors Python's {@code SandboxGatewayClientMixin} in {@code sandbox/sandbox_mixin.py}.
 */
public class SandboxGatewayClientMixin {

    private static final String TEMPLATE_SESSION_PLACEHOLDER = "{session_id}";

    protected SandboxGatewayConfig config;
    protected String isolationKeyTemplate;
    protected String opType;
    private SandboxGatewayClient gatewayClient;

    /**
     * Initialize the client context with run configuration.
     *
     * @param runConfig            the sandbox run configuration
     * @param opType               operation type (fs/shell/code)
     */
    protected void initClientContext(SandboxRunConfig runConfig, String opType) {
        this.config = (SandboxGatewayConfig) runConfig.getConfig();
        this.isolationKeyTemplate = runConfig.getIsolationKeyTemplate();
        this.opType = opType;
    }

    /**
     * Resolve the isolation key template with current session_id.
     *
     * @param sessionId the session ID to use
     * @return the resolved isolation key
     */
    protected String getResolvedIsolationKey(String sessionId) {
        if (isolationKeyTemplate == null) {
            return "default_session";
        }
        if (isolationKeyTemplate.contains(TEMPLATE_SESSION_PLACEHOLDER)) {
            String resolvedSessionId = sessionId != null ? sessionId : "default_session";
            return isolationKeyTemplate.replace(TEMPLATE_SESSION_PLACEHOLDER, resolvedSessionId);
        }
        return isolationKeyTemplate;
    }

    /**
     * Get or create the gateway client.
     *
     * @param sessionId the session ID for isolation key resolution
     * @return the SandboxGatewayClient instance
     */
    protected SandboxGatewayClient getGatewayClient(String sessionId) {
        if (gatewayClient == null) {
            gatewayClient = new SandboxGatewayClient(config, getResolvedIsolationKey(sessionId));
        }
        return gatewayClient;
    }

    /**
     * Invoke a provider method through the gateway full-chain routing.
     *
     * @param sessionId the session ID
     * @param method    the method name to invoke
     * @param params    the method parameters
     * @return the invocation result
     */
    public Object invoke(String sessionId, String method, Map<String, Object> params) {
        SandboxGatewayClient client = getGatewayClient(sessionId);
        return client.invoke(opType, method, params);
    }

    /**
     * Invoke a streaming provider method through the gateway full-chain routing.
     *
     * @param sessionId the session ID
     * @param method    the method name to invoke
     * @param params    the method parameters
     * @return an iterator for streaming results
     * @throws Exception if invocation fails
     */
    public Iterator<?> invokeStream(String sessionId, String method, Map<String, Object> params) throws Exception {
        SandboxGatewayClient client = getGatewayClient(sessionId);
        return client.invokeStream(opType, method, params);
    }
}