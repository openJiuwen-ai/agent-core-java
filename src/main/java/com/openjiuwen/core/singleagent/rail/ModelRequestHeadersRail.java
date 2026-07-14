/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.openjiuwen.core.runner.callback.AbortError;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Resolves request-scoped model headers before each model call.
 */
public class ModelRequestHeadersRail extends AgentRail {
    private final ModelRequestHeadersProvider provider;

    public ModelRequestHeadersRail(ModelRequestHeadersProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    @Override
    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        if (context == null || !(context.getInputs() instanceof ModelCallInputs inputs)) {
            throw abort("Invalid model call inputs");
        }

        CompletionStage<Map<String, String>> stage;
        try {
            stage = provider.provide(context);
        } catch (RuntimeException exception) {
            throw abort("Request headers provider invocation failed");
        }
        if (stage == null) {
            throw abort("Request headers provider stage is null");
        }

        Map<?, ?> headers;
        try {
            headers = stage.toCompletableFuture().join();
        } catch (RuntimeException exception) {
            throw abort("Request headers provider completion failed");
        }
        if (headers == null) {
            throw abort("Request headers are missing");
        }

        Map<String, String> copiedHeaders;
        try {
            copiedHeaders = copyAndValidateHeaders(headers);
        } catch (HeaderValidationException exception) {
            throw abort(exception.reason());
        } catch (RuntimeException exception) {
            throw abort("Request headers copy or validation failed");
        }

        try {
            inputs.mergeRequestHeaders(copiedHeaders);
        } catch (RuntimeException exception) {
            throw abort("Request headers merge failed");
        }
        return completed();
    }

    private static Map<String, String> copyAndValidateHeaders(Map<?, ?> headers) {
        if (headers == null || headers.isEmpty()) {
            throw new HeaderValidationException("Request headers are missing");
        }

        Map<String, String> copiedHeaders = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : headers.entrySet()) {
            Object rawName = entry.getKey();
            Object rawValue = entry.getValue();
            if (!(rawName instanceof String name) || !(rawValue instanceof String value)) {
                throw new HeaderValidationException("Request headers contain invalid types");
            }
            if (name.equalsIgnoreCase("Authorization") && value.isBlank()) {
                throw new HeaderValidationException("Authorization header is blank");
            }
            copiedHeaders.put(name, value);
        }
        if (copiedHeaders.isEmpty()) {
            throw new HeaderValidationException("Request headers are missing");
        }
        return copiedHeaders;
    }

    private static AbortError abort(String reason) {
        return new AbortError(reason);
    }

    private static final class HeaderValidationException extends RuntimeException {
        private final String reason;

        private HeaderValidationException(String reason) {
            this.reason = reason;
        }

        private String reason() {
            return reason;
        }
    }
}
