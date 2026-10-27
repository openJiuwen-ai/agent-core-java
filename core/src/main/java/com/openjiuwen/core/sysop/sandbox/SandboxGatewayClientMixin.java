/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;
import com.openjiuwen.extensions.tracerotel.SemConv;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

/**
 * Mixin providing gateway client management and invoke/invokeStream for sandbox operations.
 *
 * <p>Mirrors Python's {@code SandboxGatewayClientMixin} in
 * {@code openjiuwen/core/sys_operation/sandbox/sandbox_mixin.py}.</p>
 */
public class SandboxGatewayClientMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SandboxGatewayClientMixin.class);
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
     * Injects sandbox id into OTel Context for observability.
     */
    public CompletableFuture<Object> invoke(String method, Map<String, Object> params) {
        SandboxGatewayClient client = getGatewayClient();
        Context ctx = Context.current();

        try {
            String sandboxId = client.getSandboxId();
            if (sandboxId != null && !sandboxId.isBlank()) {
                ctx = ctx.with(SemConv.CTX_SANDBOX_ID, sandboxId);
            }
        } catch (NullPointerException | CompletionException | CancellationException e) {
            // Gracefully handle sandbox ID retrieval failures (e.g., incomplete config in tests)
            LOGGER.debug("Failed to retrieve sandbox ID for observability context", e);
        }

        Context finalCtx = ctx;
        Span invocationSpan = Span.fromContext(finalCtx);
        try (Scope scope = finalCtx.makeCurrent()) {
            return client.invoke(opType, method, params).exceptionally(error -> {
                recordSandboxErrorMessage(invocationSpan, error);
                Context errorCtx = finalCtx.with(SemConv.CTX_SANDBOX_ERROR_MESSAGE, error.getMessage());
                try (Scope errorScope = errorCtx.makeCurrent()) {
                    // Propagate the exception
                    if (error instanceof CompletionException) {
                        throw (CompletionException) error;
                    }
                    throw new CompletionException("Sandbox invoke failed", error);
                }
            });
        }
    }

    /**
     * Invoke a streaming provider method through the gateway full-chain routing.
     * Injects sandbox id into OTel Context for observability.
     */
    public CompletableFuture<Flow.Publisher<?>> invokeStream(String method, Map<String, Object> params) {
        SandboxGatewayClient client = getGatewayClient();
        Context ctx = Context.current();

        try {
            String sandboxId = client.getSandboxId();
            if (sandboxId != null && !sandboxId.isBlank()) {
                ctx = ctx.with(SemConv.CTX_SANDBOX_ID, sandboxId);
            }
        } catch (NullPointerException | CompletionException | CancellationException e) {
            // Gracefully handle sandbox ID retrieval failures (e.g., incomplete config in tests)
            LOGGER.debug("Failed to retrieve sandbox ID for observability context", e);
        }

        Context finalCtx = ctx;
        Span invocationSpan = Span.fromContext(finalCtx);
        try (Scope scope = finalCtx.makeCurrent()) {
            return client.invokeStream(opType, method, params).exceptionally(error -> {
                recordSandboxErrorMessage(invocationSpan, error);
                Context errorCtx = finalCtx.with(SemConv.CTX_SANDBOX_ERROR_MESSAGE, error.getMessage());
                try (Scope errorScope = errorCtx.makeCurrent()) {
                    // Propagate the exception
                    if (error instanceof CompletionException) {
                        throw (CompletionException) error;
                    }
                    throw new CompletionException("Sandbox invokeStream failed", error);
                }
            });
        }
    }

    private static void recordSandboxErrorMessage(Span span, Throwable error) {
        String errorMessage = error.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = error.getClass().getSimpleName();
        }
        try {
            span.setAttribute(SemConv.OJ_SANDBOX_ERROR_MESSAGE, errorMessage);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException
                | ClassCastException | UnsupportedOperationException e) {
            LOGGER.warn("Failed to record sandbox error message on the active span: {}",
                    e.getClass().getSimpleName());
        }
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
