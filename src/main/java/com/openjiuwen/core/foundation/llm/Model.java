/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Unified LLM invocation entry point.
 *
 * <p>Mirrors Python's {@code Model} in
 * {@code openjiuwen/core/foundation/llm/model.py}.</p>
 */
public class Model {

    @FunctionalInterface
    public interface ModelInvoker {
        CompletionStage<AssistantMessage> invoke(
                List<BaseMessage> messages,
                ModelRequestConfig modelConfig,
                ModelClientConfig modelClientConfig,
                ModelInvokeOptions options
        );
    }

    private static final Map<String, ModelInvoker> INVOKERS = new LinkedHashMap<>();

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final ModelInvoker invoker;

    public Model(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config is none"
            );
        }
        this.modelClientConfig = modelClientConfig;
        this.modelConfig = modelConfig;
        this.invoker = resolveInvoker(modelClientConfig.getClientProvider());
    }

    public Model(ModelInvoker invoker) {
        this.modelClientConfig = null;
        this.modelConfig = null;
        this.invoker = invoker;
    }

    public static void registerInvoker(String provider, ModelInvoker invoker) {
        if (provider == null || provider.isBlank() || invoker == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "provider and invoker are required"
            );
        }
        INVOKERS.put(provider, invoker);
    }

    public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages) {
        return invoke(messages, ModelInvokeOptions.builder().build());
    }

    public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
        List<BaseMessage> resolvedMessages = messages == null ? List.of() : List.copyOf(messages);
        ModelInvokeOptions resolvedOptions = options == null ? ModelInvokeOptions.builder().build() : options;
        return invoker.invoke(resolvedMessages, modelConfig, modelClientConfig, resolvedOptions);
    }

    private static ModelInvoker resolveInvoker(String provider) {
        if (provider == null || provider.isBlank()) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config client_provider is none"
            );
        }
        ModelInvoker exact = INVOKERS.get(provider);
        if (exact != null) {
            return exact;
        }
        String normalized = provider.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, ModelInvoker> entry : INVOKERS.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        throw ErrorHelper.buildError(
                StatusCode.MODEL_PROVIDER_INVALID,
                "error_msg",
                "unavailable model provider: " + provider + ",and available providers are: " + INVOKERS.keySet()
        );
    }
}
