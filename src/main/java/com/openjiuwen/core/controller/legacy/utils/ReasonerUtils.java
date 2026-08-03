/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.utils;

import com.openjiuwen.core.common.utils.HashUtil;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Shared reasoner helpers for chat history and model resolution.
 *
 * <p>Mirrors Python's {@code ReasonerUtils} in
 * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
 */
public final class ReasonerUtils {

    private static final InMemoryResourceManager IN_MEMORY_RESOURCE_MANAGER = new InMemoryResourceManager();
    private static volatile ResourceManagerView resourceManager = IN_MEMORY_RESOURCE_MANAGER;

    private ReasonerUtils() {
    }

    public static List<BaseMessage> getChatHistory(ContextEngine contextEngine, AgentSessionApi session,
                                                   int chatHistoryMaxTurn) {
        return MessageHandlerUtils.sliceChatHistory(contextEngine, session, chatHistoryMaxTurn);
    }

    public static CompletionStage<Model> getModel(ModelConfig modelConfig) {
        return getModel(modelConfig, null);
    }

    public static CompletionStage<Model> getModel(ModelConfig modelConfig, AgentSessionApi session) {
        if (modelConfig == null) {
            CompletableFuture<Model> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("model config is none"));
            return failed;
        }
        BaseModelInfo modelInfo = modelConfig.getModelInfo() == null
                ? new BaseModelInfo()
                : modelConfig.getModelInfo();
        String modelId = HashUtil.generateKey(
                modelInfo.getApiKey(),
                modelInfo.getApiBase(),
                modelConfig.getModelProvider()
        );
        ResourceManagerView manager = resourceManager == null ? IN_MEMORY_RESOURCE_MANAGER : resourceManager;
        return manager.getModel(modelId, session)
                .thenCompose(existing -> {
                    if (existing != null) {
                        return CompletableFuture.completedFuture(existing);
                    }
                    manager.addModel(modelId, () -> createModel(modelId, modelConfig, modelInfo));
                    return manager.getModel(modelId, session);
                });
    }

    public static void setResourceManager(ResourceManagerView manager) {
        resourceManager = manager == null ? IN_MEMORY_RESOURCE_MANAGER : manager;
    }

    public static void resetResourceManager() {
        resourceManager = IN_MEMORY_RESOURCE_MANAGER;
        IN_MEMORY_RESOURCE_MANAGER.clear();
    }

    static Model createModel(String modelId, ModelConfig modelConfig, BaseModelInfo modelInfo) {
        ModelClientConfig modelClientConfig = ModelClientConfig.builder()
                .clientId(modelId)
                .clientProvider(modelConfig.getModelProvider())
                .apiKey(modelInfo.getApiKey())
                .apiBase(modelInfo.getApiBase())
                .timeout(modelInfo.getTimeout())
                .verifySsl(false)
                .sslCert(null)
                .customHeaders(modelInfo.getCustomHeaders() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(modelInfo.getCustomHeaders()))
                .build();
        ModelRequestConfig modelRequestConfig = ModelRequestConfig.builder()
                .modelName(modelInfo.getModelName())
                .temperature(modelInfo.getTemperature())
                .topP(modelInfo.getTopP())
                .extraFields(modelInfo.getExtraFields() == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(modelInfo.getExtraFields()))
                .build();
        return new Model(modelClientConfig, modelRequestConfig);
    }

    /**
     * Narrow model-resource manager surface used by {@link ReasonerUtils}.
     *
     * <p>Mirrors Python's {@code Runner.resource_mgr.get_model/add_model} access in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public interface ResourceManagerView {
        CompletionStage<Model> getModel(String modelId, AgentSessionApi session);

        void addModel(String modelId, Supplier<Model> modelSupplier);
    }

    /**
     * Minimal fallback manager used when no translated Runner resource manager is wired.
     *
     * <p>Mirrors Python's {@code Runner.resource_mgr} model cache behavior in
     * {@code openjiuwen/core/controller/legacy/utils.py}.</p>
     */
    public static final class InMemoryResourceManager implements ResourceManagerView {
        private final Map<String, Supplier<Model>> modelSuppliers = new LinkedHashMap<>();
        private final Map<String, Model> models = new LinkedHashMap<>();

        @Override
        public synchronized CompletionStage<Model> getModel(String modelId, AgentSessionApi session) {
            Model model = models.get(modelId);
            if (model == null && modelSuppliers.containsKey(modelId)) {
                model = modelSuppliers.get(modelId).get();
                models.put(modelId, model);
            }
            return CompletableFuture.completedFuture(model);
        }

        @Override
        public synchronized void addModel(String modelId, Supplier<Model> modelSupplier) {
            if (modelId != null && modelSupplier != null) {
                modelSuppliers.put(modelId, modelSupplier);
            }
        }

        synchronized void clear() {
            modelSuppliers.clear();
            models.clear();
        }
    }
}
