/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import com.openjiuwen.core.common.eventbus.EventBus;
import com.openjiuwen.core.common.eventbus.EventBusHolder;
import com.openjiuwen.core.common.eventbus.events.ModelRemovedEvent;
import com.openjiuwen.core.common.eventbus.events.ModelUpdatedEvent;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.tracer.TracerDecorator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Model resource manager with lifecycle events and dynamic resolution.
 *
 * @since 0.1.7
 */
public class ModelManager extends AbstractManager<Object> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelManager.class);

    private final EventBus eventBus;
    private volatile String defaultModelId;

    /**
     * Creates a manager that publishes lifecycle events on the shared EventBus.
     */
    public ModelManager() {
        this(EventBusHolder.getInstance());
    }

    /**
     * Creates a manager that publishes lifecycle events on the given EventBus.
     *
     * @param eventBus event bus used for model change events; null falls back to the shared bus
     * @since 0.1.15
     */
    public ModelManager(EventBus eventBus) {
        this.eventBus = eventBus != null ? eventBus : EventBusHolder.getInstance();
    }

    /**
     * Registers a model provider and publishes an update event.
     *
     * @param modelId model identifier
     * @param model model supplier
     */
    public void addModel(String modelId, Supplier<?> model) {
        registerResourceProvider(modelId, model);
        eventBus.publish(new ModelUpdatedEvent(modelId));
    }

    /**
     * Overwrites or inserts a model provider and publishes an update event.
     *
     * @param modelId model identifier
     * @param model model supplier
     * @since 0.1.15
     */
    public void updateModel(String modelId, Supplier<?> model) {
        replaceResourceProvider(modelId, model);
        eventBus.publish(new ModelUpdatedEvent(modelId));
        LOGGER.info("model updated: {}", modelId);
    }

    /**
     * Removes a model while requiring that at least one model remains.
     *
     * @param modelId model identifier
     * @return the removed supplier, or null when absent
     */
    public Supplier<?> removeModel(String modelId) {
        return removeModel(modelId, false);
    }

    /**
     * Removes a model, optionally allowing the registry to become empty.
     *
     * @param modelId model identifier
     * @param isForce whether removing the last model is allowed
     * @return the removed supplier, or null when absent
     * @since 0.1.15
     */
    public Supplier<?> removeModel(String modelId, boolean isForce) {
        if (!isForce && size() <= 1 && contains(modelId)) {
            throw new IllegalArgumentException(
                    "cannot remove the last model; at least one model must remain");
        }
        Supplier<?> removed = unregisterResourceProvider(modelId);
        if (removed == null) {
            return null;
        }
        if (modelId.equals(defaultModelId)) {
            List<String> remaining = providerIds();
            if (!remaining.isEmpty()) {
                defaultModelId = remaining.get(0);
                LOGGER.info("default model removed, new default: {}", defaultModelId);
            } else {
                defaultModelId = null;
                LOGGER.info("default model removed, no models remain");
            }
        }
        eventBus.publish(new ModelRemovedEvent(modelId));
        LOGGER.info("model removed: {}", modelId);
        return removed;
    }

    public CompletionStage<Object> getModel(String modelId) {
        return getModel(modelId, null);
    }

    public CompletionStage<Object> getModel(String modelId, Object session) {
        return getResource(modelId).thenApply(model -> TracerDecorator.decorateModelWithTrace(model, session));
    }

    /**
     * Returns registered model ids in registration order.
     *
     * @return model ids
     * @since 0.1.15
     */
    public List<String> listModelIds() {
        return new ArrayList<>(providerIds());
    }

    /**
     * Reports whether a model id is registered.
     *
     * @param modelId model identifier
     * @return whether the id is registered
     * @since 0.1.15
     */
    public boolean hasModel(String modelId) {
        return contains(modelId);
    }

    /**
     * Returns the configured default model, or the first registered model.
     *
     * @return default model when available
     * @since 0.1.15
     */
    public Optional<Model> getDefaultModel() {
        if (defaultModelId != null) {
            return Optional.ofNullable(awaitModel(defaultModelId));
        }
        List<String> ids = providerIds();
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(awaitModel(ids.get(0)));
    }

    /**
     * Sets the default model id.
     *
     * @param modelId registered model id, or null to clear
     * @since 0.1.15
     */
    public void setDefaultModelId(String modelId) {
        if (modelId != null && !contains(modelId)) {
            throw new IllegalArgumentException("model not found: " + modelId);
        }
        this.defaultModelId = modelId;
    }

    /**
     * Returns the configured default model id.
     *
     * @return default model id, or null when unset
     * @since 0.1.15
     */
    public String getDefaultModelId() {
        return defaultModelId;
    }

    /**
     * Resolves a model with priority: dynamic id, then fallback configs, then default.
     *
     * @param dynamicModelId request-scoped model id; may be null
     * @param fallbackClientConfig fallback client config
     * @param fallbackRequestConfig fallback request config
     * @return resolved model
     * @since 0.1.15
     */
    public Model resolveModel(String dynamicModelId,
                              ModelClientConfig fallbackClientConfig,
                              ModelRequestConfig fallbackRequestConfig) {
        if (dynamicModelId != null && !dynamicModelId.isBlank()) {
            Model dynamicModel = awaitModel(dynamicModelId);
            if (dynamicModel != null) {
                return dynamicModel;
            }
        }
        if (fallbackClientConfig != null && fallbackRequestConfig != null) {
            return new Model(fallbackClientConfig, fallbackRequestConfig);
        }
        Optional<Model> defaultModel = getDefaultModel();
        if (defaultModel.isPresent()) {
            return defaultModel.get();
        }
        throw new IllegalStateException("no model available: dynamic model not found ("
                + dynamicModelId + "), no fallback config provided, and no default model registered");
    }

    private Model awaitModel(String modelId) {
        try {
            Object value = getResource(modelId).toCompletableFuture().get();
            return value instanceof Model model ? model : null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CompletionException(interrupted);
        } catch (ExecutionException error) {
            throw new CompletionException(error.getCause());
        }
    }
}
